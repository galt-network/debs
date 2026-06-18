(ns debs.pwa.ui
  (:require
    [debs.shared.ui.events :as shared.events]
    [debs.shared.ui.subscriptions :as shared.subs]
    [debs.pwa.storage]
    [re-frame.core :as rf]
    [applied-science.js-interop :as j]
    [debs.shared.ui.components :as ui.components]))

(defn present
  [{:keys []}]
  (let [click-paste #(rf/dispatch [::shared.events/paste-from-clipboard])
        tweet-url (rf/subscribe [::shared.subs/tweet-url])
        valid-url? (rf/subscribe [::shared.subs/valid-tweet-url?])
        tweet-card-with-generate (fn [t]
                                   ^{:key (:tweet-id t)}
                                   [ui.components/swipeable-tweet-card
                                    (assoc t
                                           :generate-response
                                           (fn [_] (rf/dispatch [::shared.events/generate-response (:tweet-id t)]))
                                           :tag-info
                                           (rf/subscribe [::shared.subs/relative-time (:created-at t)])
                                           :remove-card
                                           (fn [_] (rf/dispatch [::shared.events/remove-card (:tweet-id t)])))])
        tweets (rf/subscribe [::shared.subs/all-tweets])]
    [:section.section
     [:div.columns.is-centered
      [:div.column.is-full-mobile.is-half-desktop
       (ui.components/pasteable-input {:on-click-paste click-paste
                                       :tweet-url tweet-url
                                       :valid-url? valid-url?})
       (doall (map tweet-card-with-generate @tweets))]]]))
