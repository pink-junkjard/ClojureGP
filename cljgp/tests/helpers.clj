

;;; cljgp.tests.helpers.clj

; Various functions and data structures used in the tests

(ns cljgp.tests.helpers
  (:use cljgp.breeding
	cljgp.selection))

; Validity checks

(defn valid-tree?
  "Does this tree fit the most fundamental requirements? That is, is it a seq or
  a valid result?"
  [tree]
  (or (coll? tree) 
      (or (symbol? tree) (number? tree)))) ; sufficient for test terminal set(s)

(defn valid-ind?
  "Returns whether given map contains keys required for individuals."
  [ind]
  (and (map? ind)
       (every? (set (keys ind)) [:func
				 :gen
				 :fitness])))


; A config for a simple gimped experiment

(def config-maths
     {:function-set [{:sym `- :arity 2}
		     {:sym `+ :arity 2}
		     {:sym `* :arity 2}]

      :terminal-set [{:sym 1}
		     {:sym 2}
		     {:sym 3}
		     {:sym 4}
		     {:sym 5}]
      :arg-list []

      :evaluation-fn (fn [x] (rand))
      :selection-fn (partial tournament-select 3)

      ;:end-condition (make-simple-end 50)
      :population-size 8

      :breeders [{:prob 0.8    :breeder-fn crossover-breeder}
		 {:prob 0.1    :breeder-fn mutation-breeder}
		 {:prob 0.1    :breeder-fn reproduction-breeder}]
      :pop-generation-fn (partial generate-ramped 7 0.5)
      })

