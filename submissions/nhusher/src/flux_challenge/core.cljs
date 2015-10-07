(ns ^:figwheel-always flux-challenge.core
  (:require [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [ajax.core :refer [GET abort]]))

(enable-console-print!)

(defonce app-state (atom {:active-planet nil
                          :ui-locked     true
                          :sith-list     [{} {} {} {} {}]}))

(defn from-planet? [planet sith] (= (-> sith :homeworld :id) (:id planet)))
(defn relationship-to [a b]
  (cond
    (= (:id a) (:id b)) :identical
    (= (-> a :master :id) (:id b)) :apprentice
    (= (-> a :apprentice :id) (:id b)) :master
    :else :no-relationship))

(defn request-sith! [id cb]
  (let [handler (fn [res] (cb (with-meta res {::cancel (fn [])})))
        req (GET (str "http://localhost:3000/dark-jedis/" id) {:response-format :json
                                                               :keywords?       true
                                                               :handler         handler})]
    (with-meta {:id id} {::cancel #(abort req)})))

(defn abort-sith-request! [sith] (if (-> sith meta ::cancel) ((-> sith meta ::cancel))))

(defn handle-active-planet-change [app-state new-planet]
  (let [planet-match (some (partial from-planet? new-planet) (:sith-list app-state))]
    (when planet-match (doseq [sith (:sith-list app-state)] (abort-sith-request! sith)))
    (assoc app-state :active-planet new-planet :ui-locked planet-match)))

(defn insert-placeholder-sith [app-state sith]
  (if (every? (partial = {}) (:sith-list app-state))
    (assoc app-state :sith-list [{} {} sith {} {}])
    app-state))

(defn insert-sith [app-state sith]
  (assoc app-state :sith-list (map #(if (= (:id %) (:id sith)) sith %) (:sith-list app-state))))

(defn reducer [app-state {:keys [type payload]}]
  (case type
    :set-active-planet (handle-active-planet-change app-state payload)
    :scroll-up (assoc app-state :sith-list (concat (take-last 3 (:sith-list app-state)) [{} {}]))
    :scroll-down (assoc app-state :sith-list (concat [{} {}] (take 3 (:sith-list app-state))))
    :sith-requested (insert-placeholder-sith app-state payload)
    :sith-received (insert-sith app-state payload)
    app-state))

(defn dispatch [action]
  (if (fn? action)
    (action dispatch)
    (reset! app-state (reducer @app-state action))))

(defn action
  ([type] {:type type})
  ([type payload] {:type type :payload payload}))

(defn scroll-up! []
  (fn [dispatch]
    (dispatch (action :scroll-up))))

(defn scroll-down! []
  (fn [dispatch]
    (dispatch (action :scroll-down))))

(defn boot! []
  (fn [dispatch]
    (dispatch (action :boot))
    (dispatch (action :sith-requested
                      (request-sith! 3616 #(dispatch (action :sith-received %)))))))

;;
;; -- UI COMPONENTS ----------------
;;

(defn sith-item [{:keys [name homeworld active]} _]
  (reify om/IRender
    (render [_]
      (html [:li.css-slot {:style (when active {:color "red"})}
             [:h3 name]
             [:h6 (:name homeworld)]]))))


(defn sith-list [{:keys [sith-list active-planet]} _]
  (reify om/IRender
    (render [_]
      (html [:ul.css-slots
             (om/build-all sith-item
                           (map #(assoc % :active (from-planet? active-planet %)) sith-list)
                           {:key :id})]))))

(defn planet-monitor [{:keys [active-planet]} _]
  (reify om/IRender
    (render [_]
      (html [:h1.css-planet-monitor (str "Obi-Wan currently on " (or (:name active-planet) "an unknown planet"))]))))

(defn controls [{:keys [ui-locked sith-list]} owner]
  (reify om/IRender
    (render [_]
      (let [dispatch (om/get-shared owner)]
        (html [:div.css-scroll-buttons
               [:button.css-button-up {:class (when ui-locked "css-button-disabled") :on-click #(dispatch (scroll-up!))}]
               [:button.css-button-down {:class (when ui-locked "css-button-disabled") :on-click #(dispatch (scroll-down!))}]])))))

(defn root-component [data _]
  (reify om/IRender
    (render [_]
      (html [:div.app-container
             [:div.css-root
              (om/build planet-monitor data)
              [:section.css-scrollable-list
               (om/build sith-list data)
               (om/build controls data)]]]))))

(om/root root-component app-state {:target (. js/document (getElementById "app"))
                                   :shared dispatch})

(defn handle-ws-message [e]
  (dispatch (action :set-active-planet (js->clj (js/JSON.parse (.-data e)) :keywordize-keys true))))

(defonce ws (doto (js/WebSocket. "ws://localhost:4000")
              (.addEventListener "message" #(handle-ws-message %))))

(dispatch (boot!))

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )

