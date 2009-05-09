
;;; cljgp.tools.analyse.clj

(ns cljgp.tools.analyse
  "Basic analysis functions, see cljgp.tools.logging for example usage."
  (:use cljgp.util))





(defn pop-avg-of
  "Returns the average of the results of mapping func over pop."
  [func pop]
  (/ (reduce + (map func pop))
     (count pop)))

; pop-min-of and max-of are not very efficient, but will likely suffice for
; basic stat gathering
(defn pop-min-of
  "Returns the minimum of the results of mapping func over pop."
  [func pop]
  (apply min (map func pop)))

(defn pop-max-of
  "Returns the maximum of the results of mapping func over pop."
  [func pop]
  (apply max (map func pop)))

; First iteration used a sort-by on :fitness, second was a much faster but
; uglier manual loop-recur, this version seems to be best of both worlds.
(defn best-fitness
  "Returns the individual with the best (lowest) fitness in the population."
  [pop]
  (apply (partial min-key get-fitness) pop)) ; min-key works fine with accessors

(defn tree-size-ind
  "Returns number of nodes (both functions and terminals) in given individual's
  tree."
  [ind]
  (tree-size (get-fn-body (get-func ind))))

(defn tree-depth-ind
  "Returns max depth of given individual's tree."
  [ind]
  (tree-depth (get-fn-body (get-func ind))))

(defn fitness-all
  "Returns minimum, maximum and average fitness in a single loop over the
  population. This is much faster than calling fitness-min, fitness-max, and
  fitness-avg separately."		; of course, fast is not pretty
  [pop]
  (loop [fit-ind (float (get-fitness (first pop)))
	 poprest (rest pop)
	 fit-min Float/MAX_VALUE
	 fit-max Float/MIN_VALUE
	 fit-tot (float 0)]
    (let [fmin (float (min fit-min fit-ind))
	  fmax (float (max fit-max fit-ind))
	  ftot (+ fit-tot fit-ind)]
      (if (seq poprest)
	(recur (float (get-fitness (first poprest))) 
	       (rest poprest)
	       fmin fmax ftot)
	{:fit-min fmin 
	 :fit-max fmax 
	 :fit-avg (/ ftot (count pop))}))))

; These defs were macro'd, but this seemed to obfuscate things unnecessarily
(def fitness-avg (partial pop-avg-of get-fitness))
(def fitness-min (partial pop-min-of get-fitness))
(def fitness-max (partial pop-max-of get-fitness))

(def tree-depth-avg (partial pop-avg-of tree-depth-ind))
(def tree-depth-min (partial pop-min-of tree-depth-ind))
(def tree-depth-max (partial pop-max-of tree-depth-ind))

(def tree-size-avg (partial pop-avg-of tree-size-ind))
(def tree-size-min (partial pop-min-of tree-size-ind))
(def tree-size-max (partial pop-max-of tree-size-ind))

; List of stats that will be present in the stats-map
(def *all-stats*
     `[fitness-avg fitness-min fitness-max fitness-all
       tree-depth-avg tree-depth-min tree-depth-max
       tree-size-avg tree-size-min tree-size-max
       best-fitness])

(defmacro make-stats-map
  "Build a map of stat function names to delayed applications of those functions
  to the given population.

  By storing this map in metadata, we can avoid re-calculating the statistics
  for different logging/output functions that report on the same seq."
  [pop]
  (reduce (fn [m func] 
	     (conj m [(keyword (name func)) `(delay (~func ~pop))])) 
	   {} 
	   *all-stats*))

(defn get-stat
  "Gets a key from the stats map, which consists of delays that need forcing."
  [stats-map stat-key]
  (force (stat-key stats-map)))