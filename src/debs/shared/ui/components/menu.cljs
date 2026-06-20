(ns debs.shared.ui.components.menu
  (:require
    [re-frame.core :as rf]
    [debs.shared.ui.events :as shared.events]
    [debs.shared.ui.subscriptions :as shared.subs]))

(defn show-instructions
  []
  (let [selected-options @(rf/subscribe [::shared.subs/selected-prompt-options])
        instructions [{:title "Response tone"
                       :id :response-tone
                       :default :neutral
                       :options [{:label "Neutral"         :id :neutral}
                                 {:label "Witty"           :id :witty}
                                 {:label "Ironic"          :id :ironic}
                                 {:label "Confrontational" :id :confrontational}]}
                      {:title "Response length"
                       :id :response-length
                       :default :tweet
                       :options [{:label "Tweet (280)"  :id :tweet}
                                 {:label "Double tweet" :id :double-tweet}
                                 {:label "3 paragraphs" :id :three-paragraphs}
                                 {:label "Detailed"     :id :detailed}]}]]
    [:div
     (map (fn [option-group]
            [:div.field {:key (:id option-group)}
             [:label.label (:title option-group)]
             [:div.control
              [:div.tags.are-medium
               (map (fn [option]
                      [:button.tag.is-rounded.is-hoverable
                       {:key (:id option)
                        :class (when (= (:id option) (get selected-options (:id option-group))) [:is-primary])
                        :on-click #(rf/dispatch [::shared.events/select-option (:id option-group) (:id option) (:default option-group)])}
                       (:label option)])
                    (:options option-group))]]])
          instructions)]))

(defn show-settings
  []
  [:div.content "These are some settings"])

(defn show-download
  []
  [:div.content
   [:h3 "You can use deBS on your desktop as browser extension"]
   [:ol
    [:li "Download the ZIP file from the following "
     [:a {:href "https://github.com/galt-network/debs/releases" :target "_blank"} "link"]]
    [:li "Extract/unzip it to a folder."]
    [:li "Go to " [:code "chrome://extensions/"] " (or edge://)."]
    [:li "Toggle " [:strong "Developer mode"] " (top right)."]
    [:li "Click " [:strong "Load unpacked"] " → select the folder."]]])

(defn show-info
  []
  [:div.content
   [:p "deBS - A tool that helps to unveil and refute collectivist fallacies and lies"]
   [:p "Source code available at: "
    [:a {:href "https://github.com/galt-network/debs" :target "_blank"} "https://github.com/galt-network/debs"]]])

(defn menu
  []
  (let [selected-item @(rf/subscribe [::shared.subs/menu-selection])
        menu-items [{:id :instructions
                     :title "Prompt & answer options (tone, length, etc.)"
                     :icon :fa-terminal
                     :view show-instructions}
                    ; {:id :settings
                    ;  :title "Edit the settings (API endpoints, keys, etc.)"
                    ;  :icon :fa-gear
                    ;  :view show-settings}
                    {:id :download
                     :title "Download the browser extension"
                     :icon :fa-download
                     :view show-download}
                    {:id :info
                     :title "Info about the deBS app"
                     :icon :fa-circle-info
                     :view show-info}]
        selected-item-data (first (filter #(= selected-item (get % :id)) menu-items))
        selected-item-view (:view selected-item-data)]
    [:<>
     [:div#button-toolbar.field.has-addons
      (map (fn [{:keys [id title icon]}]
             [:p.control {:key id}
              [:button.button {:title title
                               :class [(when (= id selected-item) :is-active)]
                               :on-click #(rf/dispatch [::shared.events/toggle-menu-item id])}
               [:span.icon.is-small
                [:i.fas.fa-solid {:class [icon]}]]]])
           menu-items)]
     (when selected-item-view [:div.card [:div.card-content [selected-item-view]]])]))
