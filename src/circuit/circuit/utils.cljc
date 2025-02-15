(ns circuit.utils)

(defn with-try-catch [f catch-fn]
  (fn [& args]
    (try
      (apply f args)
      (catch #?(:clj Exception :cljs js/Object) e (catch-fn e args)))))

(def safe-meta  (with-try-catch meta (constantly nil)))

(def safe-derive  (with-try-catch derive (fn [_ [hierarchy :as _args]] hierarchy)))