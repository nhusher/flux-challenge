(ns ^:figwheel-always flux-challenge.core
  (:require [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [ajax.core :refer [GET abort]]))

(enable-console-print!)

(defonce app-state (atom {:active-planet nil
                          :ui-locked     true
                          :sith-list     [nil nil nil nil nil]}))

(defn get-sith [id cb]
  (let [handler (fn [res] (cb (with-meta res {::cancel (fn [])})))
        req (GET (str "http://localhost:3000/dark-jedis/" id) {:response-format :json
                                                               :keywords?       true
                                                               :handler         handler})]
    (with-meta {:id id} {::cancel #(abort req)})))

(defn abort-sith-request! [sith] (if (-> sith meta ::cancel) ((-> sith meta ::cancel))))

(defn from-planet? [planet sith] (= (-> sith :homeworld :id) (:id planet)))

(defn relationship-to [a b]
  (cond
    (= (:id a) (:id b)) :identical
    (= (-> a :master :id) (:id b)) :apprentice
    (= (-> a :apprentice :id) (:id b)) :master
    :else :no-relationship))

(defn get-sith-slot [sith sith-list]
  (if (every? (partial = {}) sith-list)
    0
    (first (filter some? (map-indexed (fn [idx e] (case (relationship-to sith e)
                                                    :master (dec idx)
                                                    :apprentice (inc idx)
                                                    :identical idx
                                                    nil)) sith-list)))))


(declare reducer)

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

(defn request-sith! [id]
  (fn [dispatch]
    (dispatch (action :sith-requested
                      (get-sith id (fn [sith]
                                     (let [sith-list (:sith-list app-state)
                                           slot (get-sith-slot sith sith-list)]
                                       (when (> slot 0) (request-sith! (:master sith)))
                                       (when (< slot 4) (request-sith! (:apprentice sith))))
                                     (dispatch (action :sith-received sith))))))))

(defn boot! []
  (fn [dispatch]
    (dispatch (action :boot))
    (request-sith! 3616)))

(defn handle-active-planet-change [app-state new-planet]
  (let [planet-match (some (partial from-planet? new-planet) (:sith-list app-state))]
    (when planet-match (doseq [sith (:sith-list app-state)] (abort-sith-request! sith)))
    (assoc app-state :active-planet new-planet :ui-locked planet-match)))

(defn insert-placeholder-sith [app-state sith]
  (let [sith-list (:sith-list app-state)
        slot (get-sith-slot sith sith-list)]
    (assoc app-state :sith-list (assoc slot sith-list sith))))

(defn receive-sith [app-state sith]
  (let [sith-list (:sith-list sith)
        slot (get-sith-slot sith sith-list)]
    (if (< -1 slot 4)
      (assoc app-state :sith-list (assoc sith-list slot sith))
      app-state)))

(defn reducer [app-state {:keys [type payload]}]
  (case type
    :set-active-planet (handle-active-planet-change app-state payload)
    :scroll-up (assoc app-state :sith-list (concat (take-last 3 (:sith-list app-state)) [{} {}]))
    :scroll-down (assoc app-state :sith-list (concat [{} {}] (take 3 (:sith-list app-state))))
    :sith-requested (insert-placeholder-sith app-state payload)
    :sith-received (receive-sith app-state payload)
    app-state))

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

