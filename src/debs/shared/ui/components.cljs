(ns debs.shared.ui.components
  (:require
   [applied-science.js-interop :as j]
   [reagent.core :as r]
   [debs.shared.ui.browser-helpers :as bh]))

(defn prefilled-reply-url
  [tweet-id response]
  (bh/build-url "https://x.com/intent/tweet" {"in_reply_to" tweet-id "text" response}))

(defn card-content
  [{:keys [tweet-id tweet-url tag-info text generate-response response response-progress] :as params}]
  (let [padding-size "p-2"
        response-text (:text response)
        {:keys [progress done?]} response-progress]
    [:div.card-content {:class [padding-size]}
     [:p {:class ["is-flex" "is-flex-wrap-wrap" "is-justify-content-space-between" "is-align-items-center"]}
      [:a {:href tweet-url :target "_blank"} tweet-url]
      [:span.tag.mb-3.mr-5 @tag-info]]
     [:div.content [:blockquote.is-italic text]]
     (when-not (nil? response-progress)
       (if (or (> 100 progress) (not done?))
         [:progress.progress {:value progress :max 100}]
         [:div.content response-text]))]))

(defn icon-text
  [icons text]
  [:span.icon-text
   [:span.icon [:i {:class (into [:fas] icons)}]]
   [:span text]])

(defn card-footer
  [{:keys [tweet-id tweet-url tag-info text generate-response response response-progress] :as params}]
  (let [padding-size "p-2"
        response-text (:text response)]
    (if (nil? response-text)
      [:footer.card-footer
       [:div.card-footer-item {:class [padding-size]}
        [:a.button.is-success.is-fullwidth {:on-click generate-response} "de-bullshit"]]]
      [:footer.card-footer
       [:a.card-footer-item {:class [padding-size] :on-click #(bh/copy-to-clipboard response-text)}
        [icon-text [:fa-solid :fa-copy] "Copy"]]
       [:a.card-footer-item {:class [padding-size] :href (prefilled-reply-url tweet-id response-text) :target "_blank"}
        [icon-text [:fa-solid :fa-bullhorn] "Post"]]
       [:a.card-footer-item {:class [padding-size] :on-click generate-response}
        [icon-text [:fa-solid :fa-repeat] "New answer"]]])))

(defn tweet-card
  [{:keys [tweet-id tweet-url tag-info text generate-response response response-progress] :as params}]
  [:div.card {:key tweet-id}
   [card-content params]
   [card-footer params]])

(defn pasteable-input
  [{:keys [on-click-paste tweet-url valid-url?]}]
  [:div.field.has-addons
   [:div.control.is-expanded.has-icons-right
    [:input.input {:placeholder "Tweet link e.g. https://x.com/HoppeQuotes/status/1175418866035515402"
                   :value @tweet-url
                   :on-change identity
                   :class (if @valid-url? ["is-success"] ["is-danger"])}]
    [:span.icon.is-small.is-right {:class (when @valid-url? ["has-text-success"])}
     [:i.fas.fa-check]]]
   [:div.control
    [:button.button.is-info {:on-click on-click-paste}
     "Paste"]]])

(defn swipeable-tweet-card
  "A Bulma .card that supports:
   - Long press anywhere on the card (especially the .card-content area) → dispatches :long-press-card
   - Left swipe-drag to delete with live visual displacement + fly-off animation"
  [{:keys [tweet-id tweet-url tag-info text generate-response response response-progress remove-card] :as params}]
  (let [state (r/atom {:dragging? false
                       :start-x   0
                       :start-y   0
                       :current-x 0
                       :lp-timer  nil})]
    (fn [{:keys [tweet-id tweet-url tag-info text generate-response response response-progress remove-card] :as params}]
      (let [{:keys [dragging? current-x]} @state
            fly-off-and-remove (fn []
                                 (swap! state assoc :current-x -1200 :dragging? false)
                                 (js/setTimeout
                                   (fn []
                                     (remove-card)
                                     (swap! state assoc :current-x 0))   ; safety reset
                                   280))]
        [:div.card
         {:key tweet-id
          :style {:transform   (when (or dragging? (not= current-x 0))
                                 (str "translateX(" current-x "px)"))
                  :transition  (if dragging?
                                 "none"
                                 "transform 0.25s cubic-bezier(0.4, 0, 0.2, 1)")
                  :will-change "transform"
                  :touch-action "pan-y"}   ; allow vertical scroll, claim horizontal gestures

          :on-touch-start
          (fn [e]
            (let [t (aget (.-touches e) 0)
                  x (.-clientX t)
                  y (.-clientY t)]
              ; Vibrate once directly in the onTouchStart handler to avoid the chrome intervention:
              ;   Blocked call to navigator.vibrate because user hasn't tapped on the frame or any embedded frame yet
              ; (when (and js/navigator js/navigator.vibrate) (js/navigator.vibrate 30))
              (swap! state assoc
                     :start-x   x
                     :start-y   y
                     :current-x 0
                     :dragging? false)
              (when-let [timer (:lp-timer @state)]
                (js/clearTimeout timer))
              (swap! state assoc :lp-timer
                     (js/setTimeout
                       (fn []
                         (when (and (not (:dragging? @state))
                                    (< (Math/abs (:current-x @state)) 8))
                           (js/console.log "swipable-card received long-press" [:long-press-card tweet-id])))
                       550))))

          :on-touch-move
          (fn [e]
            (let [t  (aget (.-touches e) 0)
                  x  (.-clientX t)
                  y  (.-clientY t)
                  dx (- x (:start-x @state))
                  dy (- y (:start-y @state))]
              ;; Cancel long-press on any significant movement
              (when-let [timer (:lp-timer @state)]
                (when (> (+ (* dx dx) (* dy dy)) 20)
                  (js/clearTimeout timer)
                  (swap! state assoc :lp-timer nil)))

              ;; Only apply horizontal drag when movement is mostly horizontal
              (let [horizontal-intent? (or (:dragging? @state)
                                           (> (Math/abs dx) (* 0.8 (Math/abs dy))))]
                (when horizontal-intent?
                  (let [clamped-x (min 0 dx)]          ; only allow left swipe
                    (swap! state assoc
                           :current-x clamped-x
                           :dragging? true))))))

          :on-touch-end
          (fn [_]
            (when-let [timer (:lp-timer @state)]
              (js/clearTimeout timer)
              (swap! state assoc :lp-timer nil))

            (let [final-x (:current-x @state)]
              (if (and (:dragging? @state) (< final-x -90))
                ;; === Successful left swipe → fly off and remove ===
                (fly-off-and-remove)
                ;; Snap back
                (swap! state assoc :current-x 0 :dragging? false))))

          :on-touch-cancel
          (fn [_]
            (when-let [timer (:lp-timer @state)]
              (js/clearTimeout timer))
            (swap! state assoc :current-x 0 :dragging? false :lp-timer nil))}
         [:button.delete.is-small {:style {:position "absolute"
                                           :top "0.5rem"
                                           :right "0.5rem"
                                           :z-index "10"}
                                   :aria-label "Delete this card"
                                   :on-click fly-off-and-remove}]
         [card-content params]
         [card-footer params]]))))
