(ns debs.pwa.ui
  (:require
    [debs.pwa.ui.events :as ui.events]
    [debs.pwa.ui.subscriptions :as ui.subs]
    [debs.pwa.storage]
    [re-frame.core :as rf]
    [applied-science.js-interop :as j]
    [debs.shared.ui.components :as ui.components]))

(defn present
  [{:keys []}]
  (let [click-paste #(rf/dispatch [::ui.events/paste-from-clipboard])
        tweet-url (rf/subscribe [::ui.subs/tweet-url])
        valid-url? (rf/subscribe [::ui.subs/valid-tweet-url?])
        tweet-card-with-generate (fn [t] (ui.components/tweet-card
                                           (assoc t :generate-response
                                                  (fn [_] (rf/dispatch [::ui.events/generate-response (:tweet-id t)]))
                                                  :tag-info (rf/subscribe [::ui.subs/relative-time (:created-at t)]))))
        tweets (rf/subscribe [::ui.subs/all-tweets])]
    [:section.section
     [:div.columns.is-centered
      [:div.column.is-full-mobile.is-half-desktop
       (ui.components/pasteable-input {:on-click-paste click-paste
                                       :tweet-url tweet-url
                                       :valid-url? valid-url?})
       (doall (map tweet-card-with-generate @tweets))]]]))
