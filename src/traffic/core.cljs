(ns traffic.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :refer [sliding-buffer put! <! >! timeout chan]]
            [traffic.car :as car]
            [traffic.state :refer [app-state]]))

(enable-console-print!)

(def DIMENSIONS [1200 600])

(def light-changing (chan (sliding-buffer 1)))

;; ============================================================================
;; Widgets

(defn freq-input [data owner {:keys [label r onChange]}]
  (reify
    om/IRenderState
    (render-state [_ {:keys [disabled?]}]
      (dom/div nil
               (dom/label nil label)
               (dom/input #js {:type "text"
                               :ref r
                               :disabled (if disabled? "" "disabled")
                               :value data
                               :onChange onChange})
               (dom/span nil "ms")))))

(defn controls [data owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (let [comm (om/get-state owner :options-comm)]
        (go (while true
              (let [k (<! comm)]
                (case k
                  :light-mode (om/transact! data [:light-mode] (fn [mode]
                                                                 (if (= mode :auto)
                                                                   :manual
                                                                   :auto)))
                  :east-west-length (om/transact! data [:east-west-green-time]
                                                  (fn [_]
                                                    (-> (om/get-node owner "east-west")
                                                        .-value)))
                  :north-south-length (om/transact! data [:north-south-green-time]
                                                    (fn [_]
                                                      (-> (om/get-node owner "north-south")
                                                          .-value)))))))))
    om/IRenderState
    (render-state [_ {:keys [options-comm]}]
      (let [auto-mode? (= :auto (:light-mode data))]
        (dom/div nil
                 (dom/div nil
                          (dom/label nil "Lights Change Automatically")
                          (dom/input #js {:type "checkbox"
                                          :onChange #(put! options-comm :light-mode)
                                          :checked (= :auto (:light-mode data))}))
                 (om/build freq-input (:north-south-green-time data)
                           {:state {:disabled? auto-mode?}
                            :opts {:label "North/South Green Light Length"
                                   :r "north-south"
                                   :onChange #(put! options-comm :north-south-length)}})

                 (om/build freq-input (:east-west-green-time data)
                           {:state {:disabled? auto-mode?}
                            :opts {:label "East/West Green Light Length"
                                   :r "east-west"
                                   :onChange #(put! options-comm :east-west-length)}}))))))

(defn light [{:keys [pos lane dim state pair] :as light} owner]
  (reify
    om/IRender
    (render [_]
      (let [[width height] dim
            [x y] pos

            style #js {:position "absolute"
                       :top y
                       :left x
                       :z-index 1
                       :width width
                       :height height
                       :opacity "0.5"
                       :cursor "pointer"
                       :background-color (name state)}]
        (dom/div #js {:style style
                      :onClick #(put! light-changing [:manual pair (if (= state :green) :red :green)])})))))

(defn car [{:keys [dir pos dim]} owner]
  (reify
    om/IRender
    (render [_]
      (let [[width height] dim
            [x y] pos

            style #js {:position "absolute"
                       :top y
                       :left x
                       :z-index 1
                       :width width
                       :height height
                       :background-color "red"}]
        (dom/div #js {:style style})))))

(defn root-widget [data owner]
  (reify
    om/IInitState
    (init-state [_]
      {:options-comm (chan)})
    om/IWillMount
    (will-mount [_]
      (go (while true
            (<! (timeout (:update-freq @data)))
            (let [cars (doall (map car/update-car (:cars @data)))]
              (om/transact! data [:cars] (fn [_] cars))))))

    om/IRenderState
    (render-state [_ state]
      (let [[width height] (:dimensions state)]
        (dom/div nil
                 (dom/div #js {:style #js {:background "url('img/background.png') no-repeat"
                                           :width (str width "px")
                                           :height (str height "px")}}
                          (apply dom/div nil
                                 (om/build-all car (:cars data)))
                          (apply dom/div nil
                                 (om/build-all light (:lights data)
                                               {:init-state state})))
                 (om/build controls (:options data)
                           {:init-state state}))))))

(om/root
  root-widget
  app-state
  {:target (. js/document (getElementById "app"))
   :init-state {:dimensions DIMENSIONS}})

;; ============================================================================
;; Managing cars

;; Adding

(defn add-car [{:keys [pos dim] :as car}]
  (let [[x y] pos
        [w h] dim]
    (when (= (count (car/filter-objs-in {:x x :y y :width w :height h}
                                        (:cars @app-state)))
             0)
      (swap! app-state update-in [:cars] conj car))))

(defn create-car
  ([pos dir]
   (let [[w h] [30 40]
         [w h] (if (or (= dir "N") (= dir "S")) [w h] [h w])]
     {:pos pos
      :dir dir
      :dim [w h]
      :state :accelerating
      :accel 0.03
      :decel 0.09
      :vel 0
      :max-vel 4}))

  ([{:keys [start dir]}]
   (create-car start dir)))

(def eastbound {:start [1 320]   :dir "E"})
(def westbound {:start [1199 268] :dir "W"})
(def southbound {:start [617 1]   :dir "S"})
(def northbound {:start [670 599]  :dir "N"})

(go (while true
      (<! (timeout 2200))
      (add-car (create-car eastbound))))

(go (while true
      (<! (timeout 2400))
      (add-car (create-car westbound))))

(go (while true
      (<! (timeout 2600))
      (add-car (create-car southbound))))

(go (while true
      (<! (timeout 2800))
      (add-car (create-car northbound))))

;; Removing

(go (while true
      (<! (timeout 2000))
      (let [cars (filter (fn [car]
                           (let [[x y] (:pos car)
                                 [mx my] DIMENSIONS]
                             (and (> x 0) (< x mx)
                                  (> y 0) (< y my))))
                         (:cars @app-state))]
        (swap! app-state assoc :cars cars))))

;; ============================================================================
;; Managing Lights

(go (while true
      (let [[mode pair light-state] (<! light-changing)
            state @app-state
            target-mode (get-in state [:options :light-mode])]
        (println mode pair light-state target-mode)
        (when (= target-mode mode)
          (let [lights (map (fn [light]
                              (if (= (:pair light) pair)
                                (assoc light :state light-state)
                                light))
                            (:lights state))]
            (swap! app-state assoc :lights lights))))
      nil))

(go (while true
      (>! light-changing [:auto :vertical :green])
      (>! light-changing [:auto :horizontal :red])
      (<! (timeout (get-in @app-state [:options :north-south-green-time])))

      (>! light-changing [:auto :vertical :red])
      (>! light-changing [:auto :horizontal :green])
      (<! (timeout (get-in @app-state [:options :east-west-green-time])))))
