(ns dtm.read
  (:require [dtm.convert :as convert]
            [dtm.util :as util]))


;;; ================================org-units===================================


(defn project [id]
  (convert/project (util/get-details :project/id id (util/get-db))))

(defn org-units [_username version]
  (let [db                (util/get-db)
        org-units-version (apply max (util/get-all-vals :org-units/version db))]
    (if (= org-units-version version)
      {:version  version}
      (let [project-uuids (util/get-all-vals :project/id db)]
        {:version  org-units-version
         :projects (mapv project project-uuids)}))))


;;; ================================tasks/pending===============================


(defn task-pending [id]
  (convert/task-pending (util/get-details :task/id id (util/get-db))))

(defn tasks-pending [username ids]
  (let [tasks-pending-ids (util/get-pending-tasks-ids username (util/get-db))
        diff              (util/diff ids (mapv str tasks-pending-ids))
        {:keys
         [insert
          delete]}        diff
        insert-uuids      (mapv util/str->uuid insert)]
    {:insert (mapv task-pending insert-uuids)
     :delete delete}))


;;; ==========================tasks/assigned/pending============================


(defn task-assigned-pending [id]
  (convert/task-assigned-pending (util/get-details :task/id id (util/get-db))))

(defn tasks-assigned-pending [username ids]
  (let [tasks-assigned-pending-ids (util/get-assigned-pending-tasks-ids
                                     username
                                     (util/get-db))
        diff                       (util/diff ids (mapv
                                                    str
                                                    tasks-assigned-pending-ids))
        {:keys
         [insert
          delete]}                 diff
        insert-uuids               (mapv util/str->uuid insert)]
    {:insert (mapv task-assigned-pending insert-uuids)
     :delete delete}))


;;; ========================tasks/assigned/completed============================


(defn task-assigned-completed [id]
  (convert/task-assigned-completed (util/get-details :task/id id (util/get-db))))

(defn tasks-assigned-completed [username ids]
  (let [tasks-assigned-completed-ids (util/get-assigned-completed-tasks-ids
                                       username
                                       (util/get-db))
        diff                         (util/diff ids
                                                (mapv
                                                  str
                                                  tasks-assigned-completed-ids))
        {:keys
         [insert
          delete]}                   diff
        insert-uuids                 (mapv util/str->uuid insert)]
    {:insert (mapv task-assigned-completed insert-uuids)
     :delete delete}))


;;; ================================tasks/completed=============================


(defn task-completed [id]
  (convert/task-completed (util/get-details :task/id id (util/get-db))))

(defn tasks-completed [username ids]
  (let [tasks-completed-ids (util/get-completed-tasks-ids username (util/get-db))
        diff                (util/diff ids (mapv str tasks-completed-ids))
        {:keys
         [insert
          delete]}          diff
        insert-uuids        (mapv util/str->uuid insert)]
    {:insert (mapv task-completed insert-uuids)
     :delete delete}))


;;; ================================tasks/tags==================================


(defn tasks-tags [_username version]
  (let [db     (util/get-db)
        tags-version (apply max (util/get-all-vals :task-tags/version db))]
    (if (= tags-version version)
      {:version version}
      (let [tags (util/get-attr :task-tags/values :task-tags/version tags-version db)]
        {:version tags-version
         :tags (vec (sort tags))}))))


;;; ================================templates/activities========================


(defn template-activity [id]
  (convert/template-activity (util/get-details :activity-template/id id (util/get-db))))

(defn templates-activities [_ ids]
  (let [activity-templates-ids  (util/get-activity-templates-ids (util/get-db))
        diff                    (util/diff ids (mapv str activity-templates-ids))
        {:keys
         [insert
          delete]}              diff
        insert-uuids            (mapv util/str->uuid insert)]
    {:insert (mapv template-activity insert-uuids)
     :delete delete}))
