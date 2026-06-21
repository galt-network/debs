(ns debs.shared.ui.events
  (:require
   [debs.shared.validations :refer [tweet-id-from-url]]
   [superstructor.re-frame.fetch-fx]
   [debs.shared.data-helpers :refer [deep-merge]]
   [re-frame.core :as rf]))

(defonce timer
  (js/setInterval (fn []
                    (println ">>> timer tick")
                    (rf/dispatch [::tick]))
                  5000))

(rf/reg-event-db
  ::tick
  (fn [db _]
    (assoc db :now (js/Date.))))

(goog-define DEBS_API_BASE_URL "http://localhost:3000/api")
(goog-define MODEL_TIMEOUT_LIMIT_SECONDS "45")

(def default-db
  {:tweet-url nil
   :now (js/Date.)
   :config {:api-base-url DEBS_API_BASE_URL
            :model-timeout-limit-seconds (js/parseInt MODEL_TIMEOUT_LIMIT_SECONDS)}
   :instructions "The response length must be 280 characters or shorter"
   :tweet-ids (list)
   :menu-selection nil
   :prompt-options {:response-length :tweet
                    :response-tone :neutral}
   :tweets {}})

(rf/reg-event-fx
 ::initialize
 [(rf/inject-cofx :debs.pwa.storage/local-storage-db)]
 (fn [{:keys [db local-storage-db]} _]
   {:db (deep-merge default-db db (or (dissoc local-storage-db :menu-selection :config :now) {}))}))

(rf/reg-fx
  ::read-clipboard
  (fn [{:keys [on-success on-failure]}]
    (if-let [clipboard (some-> js/navigator .-clipboard)]
      (-> (.readText clipboard)
          (.then (fn [text] (when on-success (rf/dispatch (conj on-success text)))))
          (.catch (fn [err]
                    (rf/console :error "Clipboard read failed" err)
                    (when on-failure (rf/dispatch (conj on-failure err))))))
      (do
        (rf/console :warn "Clipboard API not available (need secure context + user gesture)")
        (when on-failure
          (rf/dispatch (conj on-failure (js/Error. "Clipboard API unsupported"))))))))

(rf/reg-event-fx
  ::paste-from-clipboard
  (fn [{:keys [db]} _]
    {:db db
     :fx [[::read-clipboard
           {:on-success [::set-tweet-url]
            :on-failure [::clipboard-read-failed]}]]}))

(comment
  (update-in {:tweet-ids (list 1 2 3)} [:tweet-ids] (comp distinct conj) 3))

(rf/reg-event-fx
  ::original-tweet-success
  (fn [{:keys [db]} [_ tweet-id tweet-url result]]
    (let [new-db (-> db
                     (assoc ,,, :tweet-url nil)
                     (update-in ,,, [:tweet-ids] (comp distinct conj) tweet-id)
                     (assoc-in ,,, [:tweets tweet-id] {:loading? false
                                                       :created-at (js/Date.)
                                                       :tweet-url tweet-url
                                                       :tweet-id tweet-id
                                                       :text (-> result :body :tweets first :text)}))]
      {:db new-db
       :fx [[:debs.pwa.storage/persist-db new-db]]})))

(rf/reg-event-db
  ::original-tweet-failure
  (fn [db [_ result]]
    (assoc-in db [:original-tweet] nil)))

(rf/reg-event-fx
  ::fetch-tweet
  (fn [{:keys [db]} [_ tweet-url]]
    (let [tweet-id (tweet-id-from-url tweet-url)]
      {:db (-> db
               (assoc-in ,,, [:tweets tweet-id :loading?] true))
       :fx [[:fetch
             {:method :get
              :url (str (get-in db [:config :api-base-url]) "/tweet-info")
              :mode :cors
              :credentials :omit
              :params {:id tweet-id}
              :response-content-types {#"application/.*json" :json}
              :on-success [::original-tweet-success tweet-id tweet-url]
              :on-failure [::original-tweet-failure tweet-id]}]]})))

(rf/reg-event-fx
  ::set-tweet-url
  (fn [{:keys [db]} [_ url share]]
    (println ">>> ::set-tweet-url" {:url url :share share})
    (let [tweet-id (tweet-id-from-url url)]
      {:db (assoc-in db [:tweet-url] url)
       :fx [(when tweet-id [:dispatch [::fetch-tweet url]])]})))

(rf/reg-event-db
  ::clipboard-read-failed
  (fn [db [_ err]]
    ;; TODO show a notification (probably due to not having given permissions)
    (rf/console :error "Failed to paste from clipboard:" err)
    db))

(defn clear-response-progress-interval
  [db tweet-id]
  (when-let [interval-id (get-in db [:tweets tweet-id :response-progress :interval-id])]
    (js/clearInterval interval-id)))

(rf/reg-event-fx
  ::generation-success
  (fn [{:keys [db]} [_ tweet-id result]]
    (clear-response-progress-interval db tweet-id)
    (let [new-db (-> db
                    (assoc-in ,,, [:tweets tweet-id :response] {:tweet-id tweet-id :text (-> result :body :response)})
                    (assoc-in ,,, [:tweets tweet-id :response-progress] {:progress 100 :done? true}))]
      {:db new-db
       :fx [[:debs.pwa.storage/persist-db new-db]]})))

(rf/reg-event-db
  ::generation-failure
  (fn [db [_ tweet-id result]]
    (clear-response-progress-interval db tweet-id)
    (-> db
        (assoc-in ,,, [:tweets tweet-id :response-progress] {:progress 100 :done? true})
        (assoc-in ,,, [:tweets tweet-id :response] nil))))

(rf/reg-event-db
  ::response-progress-tick
  (fn [db [_ tweet-id]]
    (if-let [start-time (get-in db [:tweets tweet-id :response-progress :start-time])]
      (let [timeout-limit-seconds (get-in db [:config :model-timeout-limit-seconds])
            elapsed (- (js/Date.now) start-time)
            total-millis (* timeout-limit-seconds 1000)
            progress (min 100 (* 100 (/ elapsed total-millis)))]
        (assoc-in db [:tweets tweet-id :response-progress :progress] progress))
      db)))

(defn format-instructions
  [{:keys [response-tone response-length]}]
  (let [lengths {:tweet "up to 280 characters"
                 :double-tweet "up to 560 characters"
                 :three-paragraphs "three paragraphs"
                 :detailed "detailed instructive explanation up to 5 paragraphs"}]
    (str
      "<response-tone>" (name response-tone) "</response-tone>\n"
      "<response-length>" (get lengths response-length) "</response-length>\n")))

(rf/reg-event-fx
  ::generate-response
  (fn [{:keys [db]} [_ tweet-id]]
    {:db (-> db
             (assoc-in ,,, [:tweets tweet-id :response-progress]
                       {:start-time (js/Date.now)
                        :progress 0
                        :interval-id (js/setInterval #(rf/dispatch [::response-progress-tick tweet-id]) 100)}))
     :fx [[:fetch
           {:method :post
            :url (str (get-in db [:config :api-base-url]) "/generator")
            :mode :cors
            :credentials :omit
            :request-content-type :json
            :body {:original_text (get-in db [:tweets tweet-id :text])
                   :instructions {:response-tone (get-in db [:prompt-options :response-tone])
                                  :response-length (get-in db [:prompt-options :response-length])}}
            :response-content-types {#"application/.*json" :json}
            :on-success [::generation-success tweet-id]
            :on-failure [::generation-failure tweet-id]}]]}))

(rf/reg-event-fx
  ::remove-card
  (fn [{:keys [db]} [_ tweet-id]]
    (let [updated-db
          (if (some #{tweet-id} (:tweet-ids db))
            (-> db
                (update ,,, :tweet-ids (partial remove #(= tweet-id %)) )
                (update ,,, :tweets dissoc tweet-id))
            db)]
      {:db updated-db
       :fx [[:debs.pwa.storage/persist-db updated-db]]})))


(defn toggle-value
  [db value-path new-value & [default]]
  (let [previous-value (get-in db value-path)
        new-value (if (= previous-value new-value) default new-value)]
    (assoc-in db value-path new-value)))

(rf/reg-event-db
  ::toggle-menu-item
  (fn [db [_ selected-item]]
    (toggle-value db [:menu-selection] selected-item)))

(rf/reg-event-db
  ::select-option
  (fn [db [_ option-key option-value default]]
    (toggle-value db [:prompt-options option-key] option-value default)))
