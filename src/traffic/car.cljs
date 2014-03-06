(ns traffic.car
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :refer [put! <! >! timeout chan]]
            [traffic.state :refer [app-state]]))

(defn obj-in? [obj [x1 y1] [x2 y2]]
  (let [[obj-width obj-height] (:dim obj)
        [ox1 oy1] (:pos obj)
        ox2 (+ ox1 obj-width)
        oy2 (+ oy1 obj-height)]
    (or (and (>= ox1 x1) (<= ox1 x2)
             (>= oy1 y1) (<= oy1 y2))
        (and (>= ox2 x1) (<= ox2 x2)
             (>= oy2 y1) (<= oy2 y2))
        (and (< x1 ox1) (> y1 oy1)
             (> x2 ox1) (< y1 oy2))
        (and (< y1 oy2) (> y2 oy1)
             (> x1 ox1) (< x1 ox2)))))

(defn filter-objs-in [{:keys [x y width height]} objs]
  (filter #(obj-in? % [x y] [(+ x width) (+ y height)])
          objs))

(defn get-stopping-distance
  "Get the rect to check for decel based on current vel and decel"
  [vel decel]
  (/ (* vel vel) (* 2 decel)))

(defn get-objs-in-front [{:keys [pos dim vel decel dir]} korks]
  (let [[x y] pos
        [width height] dim
        dist (get-stopping-distance vel decel)
        rect (case dir
               "S" {:x x :y (+ y (inc height)) :width width :height (+ height dist)}
               "E" {:x (+ x (inc width)) :y y :width (+ width dist) :height height}

               "N" {:x x :y (- y height (inc dist)) :width width :height (+ height dist)}
               "W" {:x (- x width (inc dist)) :y y :width (+ width dist) :height height})]
    (filter-objs-in rect (get-in @app-state korks))))

(defn car-in-front?
  [car]
  (let [cars (get-objs-in-front car [:cars])]
    (> (count cars) 0)))

(defn red-lights-in-front?
  [car]
  (let [lights (get-objs-in-front car [:lights])]
    (> (count
         (filter #(= (:state %) :red) lights)) 0)))

(defn get-new-position
  [[x y] dir vel]
  (case dir
    "N" [x (- y vel)]
    "S" [x (+ y vel)]
    "E" [(+ x vel) y]
    "W" [(- x vel) y]))

(defn update-car [{:keys [pos state dir vel accel decel max-vel] :as car}]
  (let [[x y] (get-new-position pos dir vel)
        new-vel (case state
                  :accelerating (+ vel accel)
                  :slowing (- vel decel)
                  :stopped 0
                  vel)]
    (-> car
        (assoc :pos [x y])
        (assoc :vel (cond
                      (> new-vel max-vel) max-vel
                      (< new-vel 0) 0
                      :else new-vel))
        (assoc :state (cond
                        (and (not (car-in-front? car)) (not (red-lights-in-front? car))) :accelerating
                        (and (= :slowing state) (= 0 new-vel)) :stopped
                        (= :accelerating state) :slowing
                        :else state)))))
