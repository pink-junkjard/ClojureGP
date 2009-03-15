
;;; cljgp.random.clj

(ns cljgp.random)

; TODO: provide a way of initing RNG(s) with seed(s)
; TODO: slot in better RNG than java's

(defn gp-rand
  "Identical to clojure.core/rand, but possibly with a different PRNG."
  ([] (. Math random))
  ([n] (* n (gp-rand))))

(defn gp-rand-int
  "Identical to clojure.core/rand-int, but using gp-rand internally."
  [n]
  (int (gp-rand n)))

(defn pick-rand
  "Returns a random item from the given collection."
  [coll]
  (nth coll (gp-rand-int (count coll))))

