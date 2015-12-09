(ns ^:figwheel-always flux-challenge.core
  (:require [cljs.pprint :as pp]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [ajax.core :refer [GET abort]]))

(enable-console-print!)

(def unknown-world {:id -1 :name "an unknown world"})

(def init-state
  {:current-planet unknown-world
   :sith           [{:id 100 :name "Nick" :homeworld "Boston" :apprentice 101 :master 99 }
                    {:id 101 :name "Tristan" :homeworld "North Hero" :master 100 :apprentice 102 }
                    {:loaded false}
                    {:loaded false}
                    {:loaded false}]})

(def app-state (atom init-state))

(defmulti read om/dispatch)

(defmethod read :current-planet
  [{:keys [state] :as env} key params]
  (let [st @state]
    (if-let [planet (:current-planet st)]
      {:value planet}
      {:value unknown-world})))

(defn missing-sith-ids [ast state values]
  (prn state)
  (prn values)
  (prn ast)
  (let [siths (map (fn [path] (get-in state path)) values)]
    (prn "CC" (into [] siths))
    [{ :sith/by-id 100 }]))

(defmethod read :sith
  [{:keys [state ast] :as env} key params]
  (let [st @state]
    {:value (into [] (map #(get-in st %)) (get st key))
     :missing (missing-sith-ids ast st (get st key))}))

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

(defui SithItem
       static om/Ident
       (ident [this {:keys [id]}]
              [:sith/by-id id])
       static om/IQuery
       (query [this] [:id :name :homeworld :apprentice :master])

       Object
       (render [this]
               (let [{:keys [name homeworld]} (om/props this)]
                 (dom/li #js {:className "css-slot"}
                         (dom/h3 nil name)
                         (dom/h6 nil homeworld)))))

(def sith-item (om/factory SithItem))

(defui SithTracker
       static om/IQuery
       (query [this]
              [[:current-planet (om/get-query PlanetDisplay)]
               {:sith (om/get-query SithItem)}])
       Object
       (render [this]
               (let [{:keys [current-planet sith]} (om/props this)]
                 (dom/div #js {:className "app-container"}
                          (dom/div #js {:className "css-root"}
                                   (planet-display current-planet)
                                   (dom/div #js {:className "css-scrollable-list"}
                                            (dom/ul #js {:className "css-slots"}
                                                    (map sith-item sith))))))))

(def parser (om/parser {:read read :mutate mutate}))


(def reconciler
  (om/reconciler
    {:state  init-state
     :parser parser}))

 (om/add-root! reconciler SithTracker (js/document.getElementById "app"))


;(defn handle-ws-message [e]
;  (let [planet (js->clj (js/JSON.parse (.-data e)) :keywordize-keys true)]
;    (om/transact! reconciler `[(planet/change ~planet)])
;    #_(swap! app-state assoc :current-planet planet)))
;
;(defonce ws (doto (js/WebSocket. "ws://localhost:4000")
;              (.addEventListener "message" #(handle-ws-message %))))
;
