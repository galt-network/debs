(ns debs.ext.side-panel.ui
  (:require
    [debs.shared.ui.subscriptions :as shared.subs]
    [debs.shared.ui.events :as shared.events]
    [debs.shared.ui.components :as shared.components]
    [re-frame.core :as rf]))

(defn present
  [{:keys []}]
  (let [click-paste #(rf/dispatch [::shared.events/paste-from-clipboard])
        tweet-url (rf/subscribe [::shared.subs/tweet-url])
        valid-url? (rf/subscribe [::shared.subs/valid-tweet-url?])
        tweet-card-with-generate (fn [t] (shared.components/tweet-card
                                           (assoc t
                                                  :generate-response
                                                  (fn [_] (rf/dispatch [::shared.events/generate-response (:tweet-id t)]))
                                                  :tag-info
                                                  (rf/subscribe [::shared.subs/relative-time (:created-at t)]))))
        tweets (rf/subscribe [::shared.subs/all-tweets])]
    [:section.section
     [:div.columns.is-centered
      [:div.column.is-full-mobile.is-half-desktop
       (shared.components/pasteable-input {:on-click-paste click-paste
                                       :tweet-url tweet-url
                                       :valid-url? valid-url?})
       (doall (map tweet-card-with-generate @tweets))]]]))
