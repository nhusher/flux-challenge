(ns ^:figwheel-always flux-challenge.core
  (:require [cljs.pprint :as pp]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [ajax.core :refer [GET abort]]))

(enable-console-print!)

(def app-state
  (atom {:current-planet { :id -1 :name "an unknown world" }}))

(defn read [{:keys [state] :as env} key params]
  (let [st @state]
    (if-let [[_ value] (find st key)]
      {:value value}
      {:value :not-found})))

(defmulti mutate om/dispatch)

(defmethod mutate 'planet/change
  [{:keys [state] :as env} key params]
  {:action (fn [] (swap! state assoc :current-planet params))})

(defmethod mutate 'sith/add
  [{:keys [state] :as env} key params]
  {:action (fn [] nil)})

(defmethod mutate 'list/scroll-up
  [{:keys [state] :as env} key params]
  {:action (fn [] nil)})

(defmethod mutate 'list/scroll-down
  [{:keys [state] :as env} key params]
  {:action (fn [] nil)})


(defui PlanetDisplay
       static om/IQuery
       (query [this]
              [:name])
       Object
       (render [this]
               (let [{:keys [name]} (om/props this)]
                 (dom/h1 #js {:className "css-planet-monitor"}
                         (str "Obi-Wan currently on " (or name "an unknown planet"))))))
(def planet-display (om/factory PlanetDisplay))

(defui SithTracker
       static om/IQuery
       (query [this] [{:current-planet (om/get-query PlanetDisplay)}])
       Object
       (render [this]
               (let [{:keys [current-planet]} (om/props this)]
                 (prn current-planet)
                 (dom/div #js {:className "app-container"}
                          (dom/div #js {:className "css-root"}
                                   (planet-display current-planet)
                                   (dom/div #js {:className "css-scrollable-list"}))))))

(def parser (om/parser {:read read :mutate mutate }))

(def reconciler
  (om/reconciler
    {:state  app-state
     :parser parser }))

(om/add-root! reconciler SithTracker (js/document.getElementById "app"))

(defn handle-ws-message [e]
  (let [planet (js->clj (js/JSON.parse (.-data e)) :keywordize-keys true)]
    (om/transact! reconciler `[(planet/change ~planet)])
    #_ (swap! app-state assoc :current-planet planet)))

(defonce ws (doto (js/WebSocket. "ws://localhost:4000")
              (.addEventListener "message" #(handle-ws-message %))))

