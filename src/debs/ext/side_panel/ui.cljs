(ns debs.ext.side-panel.ui)

(defn tweet-card
  [tweet]
  (let [padding-size "p-2"]
    [:div.card
     [:div.card-content {:class [padding-size]}
      [:div "Response to: "
       [:span.has-text-weight-normal (:original-text tweet)]]
      [:div.content (:response tweet)]]
     [:footer.card-footer
      [:a.card-footer-item {:class [padding-size]} "Copy"]
      [:a.card-footer-item {:class [padding-size]} "Post"]
      [:a.card-footer-item {:class [padding-size]} "Save for later"]]]))

(defn present [state send-message]
  (let [last-title (get-in @state [:last-selected-post :text])
        last-response "none yet"]
    [:div
     [:div.field
      [:div.control
       [:label.checkbox
        [:input {:type "checkbox"}]
        "Analyze on add"]]]
     (tweet-card {:original-text last-title :response last-response})
     (map (fn [p] [:div.box (:text p)]) (vals (:posts @state)))

    ]))
