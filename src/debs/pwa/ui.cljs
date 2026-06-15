(ns debs.pwa.ui
  (:require
   [applied-science.js-interop :as j]
   [debs.shared.ui.components :as ui.components]))

(defn present
  [{:keys [state]}]
  [:section.section
   [:div.columns.is-centered
    [:div.column.is-full-mobile.is-half-desktop
     [:div.field
      [:label.label "Tweet link"]
      [:div.control
       [:input.input {:on-change (fn [event] (reset! state {:tweet-url (j/get-in event [:target :value])}))
                      :value (get-in @state [:tweet-url])}]]]
     [:div.field
      [:label.label "Tweet to respond to:"]
      [:div.control
       [:textarea.textarea {:read-only true}]]]
     [:div.field
      [:div.control
       [:button.button.is-primary "de-BS"]]]
     (ui.components/actionable-tweet-card {:original-text "Hello!" :response "No way" :tweet-id "1726415665228141028"})]]])
