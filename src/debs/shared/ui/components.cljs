(ns debs.shared.ui.components
  (:require
   [applied-science.js-interop :as j]
   [debs.shared.ui.browser-helpers :as bh]))

(defn prefilled-reply-url
  [tweet-id response]
  (bh/build-url "https://x.com/intent/tweet" {"in_reply_to" tweet-id "text" response}))

(defn tweet-card
  [{:keys [tweet-id tweet-url tag-info text generate-response response response-progress]}]
  (let [padding-size "p-2"
        response-text (:text response)
        {:keys [progress done?]} response-progress]
    [:div.card {:key tweet-id}
     [:div.card-content {:class [padding-size]}
      [:p {:class ["is-flex" "is-justify-content-space-between" "is-align-items-center"]}
       [:a {:href tweet-url :target "_blank"} tweet-url]
       [:span.tag.mb-3 @tag-info]]
      [:div.content [:blockquote.is-italic text]]
      (when-not (nil? response-progress)
        (if (or (> 100 progress) (not done?))
          [:progress.progress {:value progress :max 100}]
          [:div.content response-text]))]
     (if (nil? response-text)
       [:footer.card-footer
        [:div.card-footer-item {:class [padding-size]}
         [:a.button.is-success.is-fullwidth {:on-click generate-response} "de-bullshit"]]]
       [:footer.card-footer
        [:a.card-footer-item {:class [padding-size] :on-click #(bh/copy-to-clipboard response-text)} "Copy"]
        [:a.card-footer-item {:class [padding-size] :href (prefilled-reply-url tweet-id response-text) :target "_blank"} "Post"]])]))

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
