
;;; cljgp.tools.logging.clj

(ns cljgp.tools.logging
  "Facilities for printing statistics and information relating to GP runs and
  their results. Includes examples/simple default versions of logging functions.

  Example usage of showing statistics during a run:
  (last (map <file logger fn>
             (map print-details 
                  (map <graph plotter fn>
                       (generate-run ...)))))

  The (last ..) call forces the lazy seq of generations to realize, and each
  realized generation gets passed through a graph plotter, 'print-details and
  a file logger, before being discarded by 'last unless it is in fact the last
  generation. This means that side effects will be performed as soon as each
  generation is computed (which might be counter-intuitive at first) due to the
  laziness of the seq of generations returned by generate-run."
  (:use cljgp.tools.analyse)
  (:use clojure.contrib.pprint)
  (:import [java.io Writer BufferedWriter FileWriter]))


(defn print-code
  "Pretty-prints an evolved function/tree, or an individual/map."
  ([writer show-ns code]
     (write code
	    :dispatch *code-dispatch*
	    :suppress-namespaces (false? show-ns)
	    :pretty true
	    :stream writer))
  ([show-ns code] 
     (print-code true show-ns code)))

(defn #^String stringify-ind-verbose
  "Prints data on given individual to a string. If show-ns is true, namespace
  info will be included in the printed tree."
  [show-ns ind]
  (let [added-data (dissoc (merge {} ind) ; copy into normal map for dissoc
			   :gen :fitness :func)]
    (str "Data of individual:\n"
	 " Generation: " (:gen ind) "\n"
	 " Fitness: " (:fitness ind) "\n"
	 (when (not (empty? added-data)) 
	   (str " Additional data: "
		(write added-data :stream nil) "\n"))
	 " Function:\n"
	 (print-code nil show-ns (:func ind))
	 "\n")))

(defn print-individual
  "Prints individual to *out*. The 'print-type determines output style, 'show-ns
  whether namespaces are shown (default false).

  The 'print-type is one of the following keywords:
    :basic       -> Prints directly as prettified map.
    :verbose     -> Prints more formatted output than :basic (default)."
  ([print-type show-ns ind]
     (condp = print-type
       :verbose (print (stringify-ind-verbose show-ns ind))
       :basic (print-code show-ns ind)))
  ([print-type ind]
     (print-individual print-type false ind))
  ([ind]
     (print-individual :verbose false ind)))

(defn setup-stats-map
  "Checks if generation has stats-map in metadata, if yes returns it. If no
  creates and adds one and returns the generation with new metadata.

  Calls seq on generation and returns this seq'd generation, meaning it can be
  used directly in when-let/if-let."
  [generation]
  (when-let [gen (seq generation)]
    (let [gen-meta ^gen]
      (if (contains? gen-meta :stats-map)
	gen
	(with-meta gen (conj {:stats-map (make-stats-map gen)} 
			     gen-meta))))))

(defn print-best
  "Prints best individual verbosely."
  [generation]
  (when-let [gen-seq (setup-stats-map generation)]
    (let [stats (:stats-map ^gen-seq)]
      (print-individual (get-stat stats :best-fitness)))))

(defn #^String stat-string
  "Returns a string containing basic stats/info on the given
  generation/population. Ends with a newline.

  Assumes calling function has handled 'setup-stats-map, as it is used here."
  ([generation]
     (stat-string generation false))
  ([generation treestats?]
     (if-let [gen-seq (seq generation)]
       (let [gen-num (:gen (first gen-seq))
	     stats (:stats-map ^gen-seq)
	     {:keys [fit-min fit-max fit-avg]} (get-stat stats :fitness-all)]
	 (str 
	  (format "Gen %1$03d: Best: %2$.2f -- Worst: %3$.2f -- Avg: %4$.2f\n"
		  gen-num fit-min fit-max fit-avg)
	  (when treestats?
	    (format "\t Trees: avg size: %1$.2f -- avg depth: %1$.2f\n" 
		    (float (get-stat stats :tree-size-avg)) 
		    (float (get-stat stats :tree-depth-avg))))))
       "")))				; generation is nil

(defn #^String stat-string-verbose
  "Return a formatted multi-line string with stats on fitness and tree size."
  [generation]
  (if-let [gen-seq (seq generation)]
    (let [gen-num (:gen (first gen-seq))
	  stats (:stats-map ^gen-seq)
	  {:keys [fit-min fit-max fit-avg]} (get-stat stats :fitness-all)]
      (str 
       (format (str 
		"=================\n"
		"Generation %1$03d\n"
		"=================\n"
		"Trees:\n"
		"  Avg size: \t%2$.4f\n"
		"  Avg depth:\t%3$.4f\n"
		"\n"
		"Fitness:\n"
		"  Best:  \t%4$.4f\n"
		"  Worst: \t%5$.4f\n"
		"  Avg:   \t%6$.4f\n"
		"\n"
		"Best individual of generation:\n")
	       gen-num 
	       (float (get-stat stats :tree-size-avg)) 
	       (float (get-stat stats :tree-depth-avg))
	       fit-min
	       fit-max
	       fit-avg)
       (stringify-ind-verbose false (get-stat stats :best-fitness))
       "\n\n"))
    ""))

(defn print-stats
  "Prints statistics on given generation to *out*, and returns the generation
  again (possibly with :stats-map added to metadata).

  The 'stat-type is one of the following keywords:
    :basic       -> A line of basic fitness data, see 'stat-string. (default)
    :basic-trees -> As basic, but with additional tree statistics.
    :verbose     -> A multi-line formatted listing of fitness and tree stats."
  ([generation]
     (print-stats :basic generation))
  ([stat-type generation]
     (when-let [gen (setup-stats-map generation)]
       (let [statfn (condp = stat-type 
		      :basic stat-string
		      :basic-trees #(stat-string % true)
		      :verbose stat-string-verbose)]
	 (print (statfn gen))
	 (flush)
	 gen))))

(defn- log-stats-writer
  "Returns a function that writes basic information about a given generation to
  a file."
  [#^String filename stat-string-fn flush-every?]
  (let [writer (BufferedWriter. (FileWriter. filename))]
    (fn file-logger [generation]
      (let [gen (setup-stats-map (seq generation))]
	(when (seq gen)
	  (.write writer #^String (stat-string-fn gen))
	  (when flush-every? 
	    (.flush writer))
	  (when (:final ^gen)
	    (.write writer "\n\nBest individual of final generation:\n")
	    (.write writer 
		    (stringify-ind-verbose false 
					   (get-stat (:stats-map ^gen) 
						     :best-fitness)))
	    (.close writer))
	  gen)))))


(defn log-stats
  "Returns a function that takes a population/generation as arg, and writes
  statistical data on it to a file with given 'filename. If 'flush-every? is
  true, writer is forcibly flushed after every call, instead of waiting for the
  final generation when the writer is closed.

  The 'stat-type is one of the following keywords:
    :basic       -> A line of basic fitness data, see 'stat-string. (default)
    :basic-trees -> As basic, but with additional tree statistics.
    :verbose     -> A multi-line formatted listing of fitness and tree stats."
  ([#^String filename]
     (log-stats filename :basic true))
  ([#^String filename stat-type]
     (log-stats filename stat-type true))
  ([#^String filename stat-type flush-every?]
     (let [statfn (condp = stat-type 
		    :basic stat-string
		    :basic-trees #(stat-string % true)
		    :verbose stat-string-verbose)]
       (log-stats-writer filename statfn flush-every?))))


