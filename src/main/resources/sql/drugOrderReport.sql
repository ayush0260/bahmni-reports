select  p.patientID as patientId,
        p.patientName as patientName,
        p.gender as  gender,
        p.age as age,
        p.user as user,
        p.name as drugName,
        p.dose as dose,
        p.units as unit,
        p.frequency as frequency,
        p.duration as duration,
        p.route as route,
        p.startDate as startDate,
        p.stopDate as stopDate,
        p.quantity as quantity from
  (select person.gender,floor(datediff(CURDATE(), person.birthdate) / 365) AS age,drug_order.dose, dcn.concept_full_name as units,rou.concept_full_name as route,drug_order.dose_units,fre.concept_full_name as frequency,
     concat(drug_order.duration ,' ', du.concept_full_name) as duration,concat(drug_order.quantity,' ', dcn.concept_full_name) as quantity, orders.patient_id, Date(orders.date_activated) as startDate,orders.concept_id,
     concat(person_name.given_name, ' ', person_name.family_name) as patientName,
     provider.name as user,
    patient_identifier.identifier as patientID,
     IF(Date(orders.date_stopped) is NULL, orders.auto_expire_date,orders.date_stopped) as stopDate,
     IF(drug_order.drug_non_coded is NULL,drug.name,drug_order.drug_non_coded) as name from drug_order
    LEFT JOIN  orders  ON orders.order_id = drug_order.order_id
    LEFT JOIN drug ON drug.concept_id = orders.concept_id
    LEFT JOIN concept_view dcn  ON dcn.concept_id = drug_order.dose_units
    LEFT JOIN concept_view rou  ON rou.concept_id = drug_order.route
    LEFT JOIN concept_view du  ON du.concept_id = drug_order.duration_units
    LEFT JOIN order_frequency ON order_frequency.order_frequency_id = drug_order.frequency
    LEFT JOIN concept_view fre  ON order_frequency.concept_id = fre.concept_id
    LEFT JOIN person_name ON person_name.person_id = orders.patient_id
    LEFT JOIN person ON person.person_id = orders.patient_id
    LEFT JOIN patient_identifier ON patient_identifier.patient_id = orders.patient_id
    LEFT JOIN encounter_provider ON encounter_provider.encounter_id = orders.encounter_id
    LEFT JOIN provider ON provider.provider_id = encounter_provider.provider_id
  where (orders.date_activated < "#startDate#"
  and IF(Date(orders.date_stopped) is NULL, orders.auto_expire_date,orders.date_stopped) is null) or (IF(Date(orders.date_stopped) is NULL, orders.auto_expire_date,orders.date_stopped) BETWEEN "#startDate#" and "#endDate#") or (orders.date_activated  BETWEEN "#startDate#" and "#endDate# "))p;