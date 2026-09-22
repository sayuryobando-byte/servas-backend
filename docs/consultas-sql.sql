-- ============================================================================
--  PLATAFORMA DE RESERVAS DE SERVICIOS — CONSULTAS DE EXPLOTACION
--  Motor: PostgreSQL (esquema public). Modelo: V1__init_schema.sql + V2.
--  Uso:   psql -U servas -d servas  |  pgAdmin / DBeaver (base servas)
--  Todas son de LECTURA (SELECT). No modifican datos.
--  Convenciones:
--    - '00000000-0000-0000-0000-000000000000' y '2026-09-15' son PLANTILLAS:
--      reemplazalos por los valores reales (id UUID / fecha) que consultes.
--    - day_of_week va de 1 (lunes) a 7 (domingo), semana ISO.
-- ============================================================================


-- ============================================================================
-- 1. MAESTROS Y REFERENCIA
-- ============================================================================

-- 1.1 Todas las comunas (maestro territorial de Medellín)
select id, name
from comunas
order by id;

-- 1.2 Solo las comunas que estan siendo usadas por servicios
select distinct c.comuna_id, cm.name
from services c
         join comunas cm on cm.id = c.comuna_id
order by cm.name;

-- 1.3 Enumerados del modelo
select unnest(enum_range(null::document_type)) as tipos_documento;
select unnest(enum_range(null::modality)) as modalidades;
select unnest(enum_range(null::reservation_status)) as estados_reserva;
select unnest(enum_range(null::cancelled_by)) as cancelados_por;


-- ============================================================================
-- 2. USUARIOS Y PROVEEDORES
-- ============================================================================

-- 2.1 Proveedores con su cuenta de acceso
select u.id,
       u.email,
       u.is_verified,
       u.created_at,
       p.first_name,
       p.last_name,
       p.document_type,
       p.document_number,
       p.phone,
       p.birth_date
from users u
         join providers p on p.user_id = u.id
order by u.created_at desc;

-- 2.2 Usuarios registrados pero sin verificar (posibles abandonos de registro)
select u.id, u.email, u.created_at
from users u
         left join providers p on p.user_id = u.id
where u.is_verified = false
order by u.created_at;

-- 2.3 Altas de usuarios por dia (ultimos 30)
select (created_at at time zone 'America/Bogota')::date as dia, count(*) as altas
from users
where created_at >= now() - interval '30 days'
group by 1
order by 1 desc;

-- 2.4 Proveedores que aun no han creado su empresa
select p.id, p.first_name, p.last_name, p.document_number, u.email
from providers p
         join users u on u.id = p.user_id
where not exists (select 1 from companies c where c.provider_id = p.id);

-- 2.5 Proveedor por numero de documento exacto
select p.id,
       p.first_name,
       p.last_name,
       p.document_type,
       p.document_number,
       p.phone,
       u.email,
       u.is_verified
from providers p
         join users u on u.id = p.user_id
where p.document_number = 'DOCUMENTO_AQUI';
-- reemplaza placeholder

-- 2.6 Telefonos repetidos entre proveedores (posible duplicidad)
select phone, count(*) as cuantos, array_agg(document_number) as documentos
from providers
group by phone
having count(*) > 1;


-- ============================================================================
-- 3. EMPRESAS / NEGOCIOS
-- ============================================================================

-- 3.1 Todas las empresas con su proveedor
select c.id,
       c.nit,
       c.name,
       c.address,
       c.social_media,
       c.logo_url,
       c.provider_id,
       p.first_name || ' ' || p.last_name as proveedor,
       u.email
from companies c
         join providers p on p.id = c.provider_id
         join users u on u.id = p.user_id
order by c.name;

-- 3.2 Empresa por NIT exacto
select id, nit, name, description, address, social_media, logo_url
from companies
where nit = 'NIT_AQUI';
-- reemplaza placeholder

-- 3.3 Empresas sin ningun servicio publicado
select c.id, c.nit, c.name
from companies c
where not exists (select 1 from services s where s.company_id = c.id);

-- 3.4 Empresas con cantidad de servicios y rango de costos
select c.id,
       c.name,
       c.nit,
       count(s.id) as servicios,
       min(s.cost) as costo_min,
       max(s.cost) as costo_max,
       count(*)       filter (where s.is_active)           as activos
from companies c
         left join services s on s.company_id = c.id
group by c.id, c.name, c.nit
order by servicios desc;

-- 3.5 Empresas sin redes sociales declaradas (ficha incompleta)
select id, nit, name
from companies
where social_media is null
   or trim(social_media) = '';


-- ============================================================================
-- 4. CATALOGO DE SERVICIOS
-- ============================================================================

-- 4.1 Servicios activos con empresa, ciudad y modalidad
select s.id,
       s.name,
       s.modality,
       s.cost,
       s.duration_minutes,
       s.is_active,
       c.name  as empresa,
       cm.name as comuna,
       s.start_date,
       s.end_date
from services s
         join companies c on c.id = s.company_id
         left join comunas cm on cm.id = s.comuna_id
where s.is_active = true
order by c.name, s.name;

-- 4.2 Conteo por modalidad
select modality, count(*) as servicios
from services
group by modality
order by modality;

-- 4.3 Servicios presenciales por comuna
select cm.name as comuna, count(s.id) as servicios
from services s
         join comunas cm on cm.id = s.comuna_id
where s.modality = 'PRESENCIAL'
group by cm.name
order by servicios desc;

-- 4.4 Servicios vigentes hoy (dentro de su rango de fechas o sin restriccion)
select s.id, s.name, c.name as empresa, s.start_date, s.end_date
from services s
         join companies c on c.id = s.company_id
where s.is_active = true
  and (s.start_date is null or s.start_date <= current_date)
  and (s.end_date is null or s.end_date >= current_date);

-- 4.5 Servicios vencidos (end_date detras de hoy)
select s.id, s.name, c.name as empresa, s.end_date
from services s
         join companies c on c.id = s.company_id
where s.end_date < current_date;

-- 4.6 Servicios a punto de vencer (proximos 30 dias)
select s.id, s.name, c.name as empresa, s.end_date
from services s
         join companies c on c.id = s.company_id
where s.end_date between current_date and current_date + 30;

-- 4.7 Servicios activos SIN agenda configurada (no se pueden reservar)
select s.id, s.name, c.name as empresa, s.modality
from services s
         join companies c on c.id = s.company_id
where s.is_active = true
  and not exists (select 1 from schedules sc where sc.service_id = s.id);

-- 4.8 Costos por modalidad
select modality,
       count(*)            as servicios,
       round(avg(cost), 2) as costo_promedio,
       min(cost)           as costo_min,
       max(cost)           as costo_max
from services
group by modality;

-- 4.9 Busqueda por palabra clave en nombre / descripcion
select s.id, s.name, c.name as empresa, s.modality, s.cost
from services s
         join companies c on c.id = s.company_id
where s.is_active = true
  and (s.name ilike '%PALABRA%' or s.description ilike '%PALABRA%');
-- reemplaza placeholder


-- ============================================================================
-- 5. AGENDAS Y BLOQUEOS
-- ============================================================================

-- 5.1 Horarios de atencion por servicio (dia, apertura, cierre)
select s.name as servicio, c.name as empresa, sc.day_of_week, sc.start_time, sc.end_time
from schedules sc
         join services s on s.id = sc.service_id
         join companies c on c.id = s.company_id
order by s.name, sc.day_of_week, sc.start_time;

-- 5.2 Dias de la semana sin horario configurado por servicio (control operativo)
select *
from (select s.id,
             s.name,
             ARRAY(
                 select d.n
             from generate_series(1, 7) d(n)
             where not exists (
               select 1 from schedules sc
               where sc.service_id = s.id and sc.day_of_week = d.n)
           ) as dias_faltantes
      from services s
      where s.is_active = true) as disponibles
order by array_length(dias_faltantes, 1) desc nulls last;

-- 5.3 Rangos solapados en la agenda de un mismo servicio
select sc1.service_id,
       sc1.day_of_week,
       sc1.start_time as ini_a,
       sc1.end_time   as fin_a,
       sc2.start_time as ini_b,
       sc2.end_time   as fin_b
from schedules sc1
         join schedules sc2 on sc2.id > sc1.id
    and sc2.service_id = sc1.service_id
    and sc2.day_of_week = sc1.day_of_week
    and sc2.start_time < sc1.end_time
    and sc2.end_time > sc1.start_time;

-- 5.4 Todos los bloqueos de una empresa (generales y por servicio)
select bd.id,
       bd.block_date,
       bd.reason,
       bd.service_id,
       s.name                                                               as servicio,
       bd.company_id,
       c.name                                                               as empresa,
       case when bd.service_id is null then 'GENERAL' else 'ESPECIFICO' end as tipo
from blocked_dates bd
         join companies c on c.id = bd.company_id
         left join services s on s.id = bd.service_id
order by bd.block_date desc;

-- 5.5 Distribucion general vs especifico
select case when service_id is null then 'GENERAL' else 'ESPECIFICO' end as tipo,
       count(*)                                                          as bloqueos
from blocked_dates
group by 1;

-- 5.6 Bloqueos futuros de una empresa
select bd.block_date, bd.reason, s.name as servicio
from blocked_dates bd
         left join services s on s.id = bd.service_id
where bd.company_id = '00000000-0000-0000-0000-000000000000' -- reemplaza: id empresa
  and bd.block_date >= current_date
order by bd.block_date;

-- 5.7 Bloqueos que aplican a un servicio en una fecha determinada
select bd.id,
       bd.block_date,
       bd.reason,
       case when bd.service_id is null then 'GENERAL (de la empresa)' else 'ESPECIFICO (del servicio)' end as alcance
from blocked_dates bd
where bd.block_date = '2026-09-15' -- reemplaza placeholder
  and (
    bd.service_id = '00000000-0000-0000-0000-000000000000' -- reemplaza: id servicio
        or (
        bd.service_id is null
            and bd.company_id = (select company_id from services where id = '00000000-0000-0000-0000-000000000000')
        )
    );


-- ============================================================================
-- 6. CLIENTES
-- ============================================================================

-- 6.1 Cliente por numero de documento (incluye tipo)
select id, document_type, document_number, first_name, last_name, phone, email
from clients
where document_number = 'DOCUMENTO_AQUI';
-- reemplaza placeholder

-- 6.2 Clientes con total de reservas
select cl.id,
       cl.document_type,
       cl.document_number,
       cl.first_name || ' ' || cl.last_name as cliente,
       cl.phone,
       cl.email,
       count(r.id)                          as reservas
from clients cl
         left join reservations r on r.client_id = cl.id
group by cl.id
order by reservas desc;

-- 6.3 Clientes recurrentes (2 o mas reservas, mismo documento)
select cl.id,
       cl.document_type,
       cl.document_number,
       cl.first_name || ' ' || cl.last_name as cliente,
       count(*)                             as reservas
from clients cl
         join reservations r on r.client_id = cl.id
group by cl.id
having count(*) >= 2
order by reservas desc;

-- 6.4 Mismo email con documentos distintos (posible duplicidad de clientes)
select email, count(*) as n, array_agg(document_number) as documentos
from clients
group by email
having count(*) > 1;


-- ============================================================================
-- 7. RESERVAS
-- ============================================================================

-- 7.1 Listado completo (cliente, servicio, empresa, fechas, estados)
select r.id,
       r.reservation_date,
       r.start_time,
       r.end_time,
       r.status,
       cl.document_type,
       cl.document_number,
       cl.first_name || ' ' || cl.last_name as cliente,
       s.name                               as servicio,
       c.name                               as empresa,
       r.created_at,
       r.cancelled_by,
       r.cancellation_reason
from reservations r
         join clients cl on cl.id = r.client_id
         join services s on s.id = r.service_id
         join companies c on c.id = s.company_id
order by r.reservation_date desc, r.start_time;

-- 7.2 Conteo por estado (vista de negocio completa)
select r.status, count(*) as reservas
from reservations r
group by r.status
order by reservas desc;

-- 7.3 Reservas en un rango de fechas (del servicio)
select r.reservation_date,
       r.start_time,
       r.end_time,
       r.status,
       cl.first_name || ' ' || cl.last_name as cliente,
       s.name                               as servicio
from reservations r
         join clients cl on cl.id = r.client_id
         join services s on s.id = r.service_id
where r.reservation_date between '2026-09-01' and '2026-09-30' -- reemplaza rango
order by r.reservation_date, r.start_time;

-- 7.4 Reservas de un servicio
select r.reservation_date, r.start_time, r.end_time, r.status, cl.document_number
from reservations r
         join clients cl on cl.id = r.client_id
where r.service_id = '00000000-0000-0000-0000-000000000000' -- reemplaza: id servicio
order by r.reservation_date desc, r.start_time;

-- 7.5 Reservas de una empresa (todas sus servicios)
select r.id,
       r.reservation_date,
       r.start_time,
       r.status,
       s.name                               as servicio,
       cl.document_number,
       cl.first_name || ' ' || cl.last_name as cliente
from reservations r
         join services s on s.id = r.service_id
         join clients cl on cl.id = r.client_id
where s.company_id = '00000000-0000-0000-0000-000000000000' -- reemplaza: id empresa
order by r.reservation_date desc, r.start_time;

-- 7.6 Reservas de un cliente (por documento)
select r.id,
       r.reservation_date,
       r.start_time,
       r.end_time,
       r.status,
       s.name as servicio,
       c.name as empresa
from reservations r
         join clients cl on cl.id = r.client_id
         join services s on s.id = r.service_id
         join companies c on c.id = s.company_id
where cl.document_number = 'DOCUMENTO_AQUI' -- reemplaza placeholder
order by r.reservation_date desc;

-- 7.7 Reservas canceladas (con motivo y quien cancelo)
select r.reservation_date,
       r.start_time,
       cl.first_name || ' ' || cl.last_name as cliente,
       s.name                               as servicio,
       r.cancelled_by,
       r.cancellation_reason,
       r.status
from reservations r
         join clients cl on cl.id = r.client_id
         join services s on s.id = r.service_id
where r.status = 'CANCELLED'
order by r.reservation_date desc;

-- 7.8 Reservas por dia (ultimos 30)
select reservation_date as dia, count(*) as reservas
from reservations
where reservation_date >= current_date - 30
group by reservation_date
order by reservation_date desc;

-- 7.9 Reservas NO canceladas cuya fecha ya paso (control operativo, HU-036)
select r.id, r.reservation_date, r.start_time, r.status, s.name as servicio, cl.document_number
from reservations r
         join services s on s.id = r.service_id
         join clients cl on cl.id = r.client_id
where r.reservation_date < current_date
  and r.status in ('PENDING', 'CONFIRMED');

-- 7.10 Duracion real promedio de las reservas activas
select round(avg(extract(epoch from (r.end_time - r.start_time)) / 60), 1) as duracion_promedio_min
from reservations r
where r.status in ('CONFIRMED', 'COMPLETED');

-- 7.11 Dobles reservas del mismo slot (control de sobrecupo, HU-037)
select r1.service_id,
       r1.reservation_date,
       r1.start_time,
       r1.id as reserva_a,
       r2.id as reserva_b
from reservations r1
         join reservations r2 on r2.id > r1.id
    and r2.service_id = r1.service_id
    and r2.reservation_date = r1.reservation_date
    and r2.start_time < r1.end_time
    and r2.end_time > r1.start_time
where r1.status in ('PENDING', 'CONFIRMED', 'COMPLETED')
  and r2.status in ('PENDING', 'CONFIRMED', 'COMPLETED')
order by r1.service_id, r1.reservation_date, r1.start_time;


-- ============================================================================
-- 8. DISPONIBILIDAD  (schedules − blocked_dates − reservations)
-- ============================================================================

-- 8.1 Horario de atencion de un servicio para una fecha concreta
select start_time, end_time
from schedules
where service_id = '00000000-0000-0000-0000-000000000000' -- reemplaza: id servicio
  and day_of_week = extract(isodow from date '2026-09-15');
-- reemplaza placeholder fecha

-- 8.2 Slots libres de un servicio en una fecha (exactamente igual que el negocio)
with p
         as (select '2026-09-15'::date                             as fecha,                    -- reemplaza fecha '00000000-0000-0000-0000-000000000000'::uuid   as servicio_id -- reemplaza id servicio
    ),
     s as (select duration_minutes
           from services,
                p
           where id = p.servicio_id),
     h as (select start_time, end_time
           from schedules,
                p
           where service_id = p.servicio_id
             and day_of_week = extract(isodow from p.fecha)),
     reservado as (select start_time, end_time
                   from reservations,
                        p
                   where service_id = p.servicio_id
                     and reservation_date = p.fecha
                     and status in ('PENDING', 'CONFIRMED', 'COMPLETED')),
     bloqueado as (select 1
                   from blocked_dates,
                        p
                   where block_date = p.fecha
                     and (service_id = p.servicio_id
                       or (service_id is null
                           and company_id = (select company_id from services where id = p.servicio_id)))),
     slots as (select (h.start_time + (gs.n * make_interval(mins = > s.duration_minutes)))::time          as inicio, (
         h.start_time + ((gs.n + 1) * make_interval(mins = > s.duration_minutes))) ::time   as fin
               from s,
                    h
                        cross join lateral generate_series(0,
                        ((extract(epoch from (h.end_time - h.start_time)) / 60)::int / s.duration_minutes)::bigint - 1
    ) as gs(n)
    )
select sl.inicio,
       sl.fin,
       case
           when exists (select 1
                        from reservado r
                        where r.start_time < sl.fin
                          and r.end_time > sl.inicio)
               then 'OCUPADO'
           else 'LIBRE' end as estado
from slots sl
where not exists (select 1 from bloqueado);

-- 8.3 Slot especifico va ocupado o libre (para un servicio, fecha y hora)
select case
           when exists (select 1
                        from reservations
                        where service_id = '00000000-0000-0000-0000-000000000000' -- id servicio
                          and reservation_date = '2026-09-15'                     -- fecha
                          and status in ('PENDING', 'CONFIRMED', 'COMPLETED')
                          and '09:00'::time between start_time and end_time - interval '1 minute')
               then 'OCUPADO'
           else 'LIBRE' end as slot;


-- ============================================================================
-- 9. REPORTES  (equivalente SQL a ReportService)
-- ============================================================================

-- 9.1 Totales por estado + total general (ReservationReport)
select coalesce(r.status::text, 'TOTAL') as estado, count(*) as reservas
from reservations r
group by rollup (r.status)
order by grouping(r.status), reservas desc;

-- 9.2 Distribucion porcentual por estado
select status,
       count(*)                                           as reservas,
       round(100.0 * count(*) / sum(count(*)) over (), 2) as pct
from reservations
group by status
order by reservas desc;

-- 9.3 Ocupacion por servicio: reservas / capacidad semanal (periodo de 5 semanas)
with capacidad as (select sch.service_id,
                          sum(extract(epoch from (sch.end_time - sch.start_time)) / 60 / s.duration_minutes) ::int
               as slots_semana
                   from schedules sch
                            join services s on s.id = sch.service_id
                   group by sch.service_id),
     efectivas as (select service_id, count(*) as reservas
                   from reservations
                   where status in ('CONFIRMED', 'COMPLETED')
                     and reservation_date between '2026-09-01' and '2026-10-06' -- 5 semanas
                   group by service_id)
select s.id                          as servicio_id,
       c.name                        as empresa,
       s.name                        as servicio,
       coalesce(cap.slots_semana, 0) as slots_semana,
       coalesce(ef.reservas, 0)      as reservas_periodo,
       case
           when cap.slots_semana > 0
               then round(100.0 * ef.reservas / (cap.slots_semana * 5), 1)
           else 0 end                as ocupacion_pct
from services s
         join companies c on c.id = s.company_id
         left join capacidad cap on cap.service_id = s.id
         left join efectivas ef on ef.service_id = s.id
order by ocupacion_pct desc;

-- 9.4 Servicios de mayor demanda (top N) — equivale a topDemand
select s.id,
       c.name      as empresa,
       s.name      as servicio,
       count(r.id) as reservas
from services s
         join companies c on c.id = s.company_id
         left join reservations r on r.service_id = s.id
    and r.status <> 'CANCELLED'
group by s.id, c.name, s.name
order by reservas desc limit 10;
-- reemplaza el tope si quieres

-- 9.5 Demanda por comuna (servicios presenciales)
select cm.name as comuna, count(r.id) as reservas
from comunas cm
         left join services s on s.comuna_id = cm.id
         left join reservations r on r.service_id = s.id and r.status <> 'CANCELLED'
group by cm.name
order by reservas desc;

-- 9.6 Ingreso potencial por empresa (costo x reservas no canceladas)
select c.id,
       c.name                   as empresa,
       count(r.id)              as reservas_vigentes,
       coalesce(sum(s.cost), 0) as ingreso_potencial
from companies c
         join services s on s.company_id = c.id
         left join reservations r on r.service_id = s.id
    and r.status in ('PENDING', 'CONFIRMED', 'COMPLETED')
group by c.id, c.name
order by ingreso_potencial desc;

-- 9.7 Tasa de cancelacion por servicio
select s.id,
       s.name      as servicio,
       count(r.id) as total,
       count(*)       filter (where r.status = 'CANCELLED')      as canceladas, round(100.0 * count(*) filter (where r.status = 'CANCELLED')
             / nullif(count(r.id), 0), 1) as tasa_cancelacion_pct
from services s
         left join reservations r on r.service_id = s.id
group by s.id, s.name
order by tasa_cancelacion_pct desc nulls last;


-- ============================================================================
-- 10. OPERACION Y AUDITORIA
-- ============================================================================

-- 10.1 Ultimas 50 reservas creadas (auditoria de actividad)
select r.created_at at time zone 'America/Bogota' as creada_en,
       r.id,
       r.reservation_date,
       r.start_time,
       r.status,
       c.name                                     as empresa,
       s.name                                     as servicio,
       cl.document_number
from reservations r
         join services s on s.id = r.service_id
         join companies c on c.id = s.company_id
         join clients cl on cl.id = r.client_id
order by r.created_at desc limit 50;

-- 10.2 Reservas por dia de la semana (pico de demanda semanal)
select extract(isodow from reservation_date)::int as dia_semana, count(*) as reservas
from reservations
group by 1
order by 1;

-- 10.3 Hora del dia mas demandada (rango de inicio mas reservado)
select start_time as hora_inicio, count(*) as reservas
from reservations
where status <> 'CANCELLED'
group by start_time
order by reservas desc;

-- 10.4 Antiguedad: reservas confirmadas que llevan mas de X dias sin completarse
select id, reservation_date, start_time, status, created_at
from reservations
where status = 'CONFIRMED'
  and reservation_date < current_date - 30;
-- ajusta los dias si quieres

-- 10.5 Conteo aproximado de filas por tabla
select relname as tabla, n_live_tup as filas_aprox
from pg_stat_user_tables
where schemaname = 'public'
order by n_live_tup desc;

-- 10.6 Tamano en disco por tabla (informacion para backup/capacidad)
select relname as tabla, pg_size_pretty(pg_total_relation_size(c.oid)) as tamano
from pg_class c
         join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public'
  and c.relkind = 'r'
order by pg_total_relation_size(c.oid) desc;