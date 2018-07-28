(ns data.init
  (:require [data.test :as t]
            [dtm.schema :as s]
            [datomic.api :as d]
            [config.dtm :as config]
            [data.parse-users-csv :as csv]
            [ring.util.http-response :as response]))

;; (defn init
;;   ([demo _]
;;    (let [uri  config/uri]
;;      (d/delete-database uri)
;;      (d/create-database uri)
;;      (let [conn (d/connect uri)]
;;        @(d/transact conn s/schema)
;;        @(d/transact conn t/users)
;;        @(d/transact conn t/verticals)
;;        @(d/transact conn t/states)
;;        @(d/transact conn t/projects)
;;        @(d/transact conn t/org-units)
;;        @(d/transact conn t/clients)
;;        @(d/transact conn t/task-tags)
;;        @(d/transact conn t/activity-templates)
;;        ;; debug
;;        @(d/transact conn t/multimedia-sample-activity)
;;        @(d/transact conn t/multimedia-sample-activity-linking)

;;        (if demo
;;          (do @(d/transact conn t/ro-activity)
;;              @(d/transact conn t/ro-linking)
;;              @(d/transact conn t/tree-activity)
;;              @(d/transact conn t/tree-linking)
;;              @(d/transact conn t/school-activity)
;;              @(d/transact conn t/school-linking))))))


(defn init
  ([demo _]
   (let [uri config/uri]
     ;; (d/delete-database uri)
     ;; (d/create-database uri)
     (let [conn (d/connect uri)]
       ;; @(d/transact conn s/schema)
       @(d/transact conn t/task-tags)
       ;; @(d/transact conn t/org-units)
       (if (= demo "create-data-for-hih")
         (do
           @(d/transact conn (csv/projects-tx))
           (csv/users-tx (csv/users "hih_users.csv")))
         []))))


  ([{:keys [username password demo]}]
   (if (and (= username "admin")
            (= password "AgY4QqfSX2cxwdSrv29BcjHKdSVEUfqJA8GPN8jf"))
     (do
       (response/ok (init demo true))
       ;;(response/ok {:result true})
       )
     (response/unauthorized {:error "wrong credentials"}))))
