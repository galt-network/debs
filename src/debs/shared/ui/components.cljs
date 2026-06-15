(ns debs.shared.ui.components
  (:require
    [debs.shared.ui.browser-helpers :as bh]))

(defn prefilled-reply-url
  [tweet-id response]
  (bh/build-url "https://x.com/intent/tweet" {"in_reply_to" tweet-id "text" response}))

(defn actionable-tweet-card
  [{:keys [original-text response tweet-id]}]
  (let [padding-size "p-2"]
    [:div.card
     [:div.card-content {:class [padding-size]}
      [:div "Response to: "
       [:span.has-text-weight-normal original-text]]
      [:div.content response]]
     [:footer.card-footer
      [:a.card-footer-item {:class [padding-size] :on-click #(bh/copy-to-clipboard response)} "Copy"]
      [:a.card-footer-item {:class [padding-size] :href (prefilled-reply-url tweet-id response) :target "_blank"} "Post"]
      [:a.card-footer-item {:class [padding-size]} "Save for later"]]]))
