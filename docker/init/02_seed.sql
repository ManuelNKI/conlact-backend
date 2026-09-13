-- ============================================================
-- CONLAC-T - Consorcio de Lácteos de Tungurahua (Pilahuín)
-- Semana 2: Script seed.sql con Datos Maestros [BE-07]
-- ============================================================

begin;

-- ============================================================
-- 1. ASOCIACIONES (Sección 2 & 3 del documento)
-- Base territorial de Pilahuín: El Lindero, Mulanleo, Apukanlla
-- ============================================================
insert into public.associations (
    id, slug, name, short_description, history, location_text,
    latitude, longitude, arcsa_registration, agrocalidad_registration,
    sanitary_seal_text, video_url, instagram_url, tiktok_url, facebook_url,
    whatsapp, is_published
) values
(
    'a0000000-0000-0000-0000-000000000001',
    'asociacion-el-lindero',
    'Asociación El Lindero',
    'Productores de quesos artesanales de altura en el sector El Lindero a 3.600 msnm.',
    'Asociación pionera con tradición quesera artesanal heredada del proceso suizo de Queseras Bolívar. Procesan leche fresca de altura con altos estándares de calidad.',
    'Sector El Lindero, Parroquia Pilahuín, Cantón Ambato, Tungurahua (3.600 msnm)',
    -1.298500, -78.712300,
    'ARCSA-2023-LIND-001', 'AGRO-TUN-AC-014',
    'Certificación Sanitaria ARCSA y Buenas Prácticas AGROCALIDAD',
    'https://www.youtube.com/watch?v=placeholder_lindero',
    'https://instagram.com/conlact_lindero',
    'https://tiktok.com/@conlact_lindero',
    'https://facebook.com/conlactlindero',
    '+593987654321',
    true
),
(
    'a0000000-0000-0000-0000-000000000002',
    'asociacion-mulanleo',
    'Asociación Mulanleo',
    'Quesería comunitaria especializada en queso de hoja tradicional y derivados lácteos.',
    'Comunidad organizada de pequeños ganaderos dedicados a la recolección diaria de leche andina y elaboración tradicional de quesos con sabor autóctono.',
    'Comunidad Mulanleo, Parroquia Pilahuín, Cantón Ambato, Tungurahua',
    -1.289100, -78.725400,
    'ARCSA-2023-MULA-002', 'AGRO-TUN-AC-015',
    'Registro Sanitario Vigente ARCSA',
    'https://www.youtube.com/watch?v=placeholder_mulanleo',
    'https://instagram.com/conlact_mulanleo',
    'https://tiktok.com/@conlact_mulanleo',
    'https://facebook.com/conlactmulanleo',
    '+593987654322',
    true
),
(
    'a0000000-0000-0000-0000-000000000003',
    'asociacion-apukanlla',
    'Asociación Apukanlla',
    'Tradición láctea andina enfocada en quesos frescos y madurados de pastoreo libre.',
    'Productores familiares comprometidos con la economía comunitaria y la conservación de técnicas tradicionales de cuajado y moldeado artesanal.',
    'Sector Apukanlla, Parroquia Pilahuín, Cantón Ambato, Tungurahua',
    -1.305000, -78.701100,
    'ARCSA-2023-APUK-003', 'AGRO-TUN-AC-016',
    'Registro Sanitario Vigente ARCSA',
    'https://www.youtube.com/watch?v=placeholder_apukanlla',
    'https://instagram.com/conlact_apukanlla',
    'https://tiktok.com/@conlact_apukanlla',
    'https://facebook.com/conlactapukanlla',
    '+593987654323',
    true
)
on conflict (slug) do nothing;

-- ============================================================
-- 2. CATEGORÍAS (Sección 2 & 4.7 del documento)
-- ============================================================
insert into public.categories (id, name, slug, description, is_active) values
(
    'c0000000-0000-0000-0000-000000000001',
    'Quesos Frescos',
    'quesos-frescos',
    'Elaborados diariamente con leche pura de altura. Sabor suave y textura tierna (preferido por el 46,9% de consumidores).',
    true
),
(
    'c0000000-0000-0000-0000-000000000002',
    'Quesos de Hoja',
    'quesos-de-hoja',
    'Queso de pasta hilada envuelto en hoja vegetal tradicional de achira (preferido por el 38,9% de consumidores).',
    true
),
(
    'c0000000-0000-0000-0000-000000000003',
    'Quesos Semimaduros y Finas Hierbas',
    'quesos-semimaduros-hierbas',
    'Quesos con tiempo de reposo y maduración, ideales para tablas, maridajes y catas.',
    true
),
(
    'c0000000-0000-0000-0000-000000000004',
    'Derivados Lácteos y Manjares',
    'derivados-lacteos',
    'Yogurt natural de altura, dulce de leche tradicional y mantequilla de campo.',
    true
)
on conflict (slug) do nothing;

-- ============================================================
-- 3. PRODUCTOS Y VARIANTES (Precios entre $4 y $6/kg, 500g preferido)
-- ============================================================
insert into public.products (
    id, association_id, category_id, slug, name, cheese_type,
    short_description, description, origin_text, conservation,
    status, is_featured
) values
(
    'b0000000-0000-0000-0000-000000000001',
    'a0000000-0000-0000-0000-000000000001',
    'c0000000-0000-0000-0000-000000000001',
    'queso-fresco-artesanal-el-lindero',
    'Queso Fresco Artesanal El Lindero',
    'Fresco no pasteurizado de altura',
    'Queso tierno con bajo contenido de sal, elaborado con leche recién ordeñada a 3.600 msnm.',
    'Nuestro producto estrella elaborado por las familias de El Lindero. Leche de vacas alimentadas con pastizales andinos sin aditivos químicos. Ideal para el desayuno o acompañar con choclo y café.',
    'Fábrica de Lácteos El Lindero, Pilahuín',
    'Mantener refrigerado entre 2°C y 6°C. Consumir dentro de los 15 días posteriores a la entrega.',
    'published',
    true
),
(
    'b0000000-0000-0000-0000-000000000002',
    'a0000000-0000-0000-0000-000000000002',
    'c0000000-0000-0000-0000-000000000002',
    'queso-de-hoja-tradicional-mulanleo',
    'Queso de Hoja Tradicional Mulanleo',
    'Pasta hilada tradicional',
    'Auténtico queso de hoja con aroma vegetal y elasticidad perfecta.',
    'Elaborado manualmente mediante hilado en agua caliente y reposado en hoja de achira, otorgándole un aroma silvestre único y una textura deshebrable inconfundible.',
    'Comunidad Mulanleo, Pilahuín',
    'Mantener en refrigeración. Para disfrutar su elasticidad, atemperar 10 minutos antes de consumir.',
    'published',
    true
),
(
    'b0000000-0000-0000-0000-000000000003',
    'a0000000-0000-0000-0000-000000000003',
    'c0000000-0000-0000-0000-000000000003',
    'queso-andino-oregano-apukanlla',
    'Queso Andino con Orégano Silvestre',
    'Semimaduro aromatizado',
    'Queso de corteza natural infusionado con orégano de páramo.',
    'Con 30 días de maduración controlada. El toque de orégano silvestre realza las notas lácteas andinas, ideal para fundir, pizzas artesanales o tablas de degustación.',
    'Quesería Apukanlla, Pilahuín',
    'Mantener refrigerado. Envolver en papel encerado o film plástico una vez abierto.',
    'published',
    true
)
on conflict (slug) do nothing;

-- Variantes / Presentaciones
insert into public.product_variants (
    id, product_id, sku, presentation_name, weight_grams, price, stock, low_stock_threshold, is_active
) values
(
    'ba000000-0000-0000-0000-000000000001',
    'b0000000-0000-0000-0000-000000000001',
    'LIN-FRE-500',
    'Bloque 500g (Tradicional)',
    500,
    2.50,
    80,
    10,
    true
),
(
    'ba000000-0000-0000-0000-000000000002',
    'b0000000-0000-0000-0000-000000000001',
    'LIN-FRE-1000',
    'Rueda 1000g (Familiar / Restaurante)',
    1000,
    4.80,
    40,
    5,
    true
),
(
    'ba000000-0000-0000-0000-000000000003',
    'b0000000-0000-0000-0000-000000000002',
    'MUL-HOJ-PACK4',
    'Pack 4 unidades (aprox. 400g)',
    400,
    2.40,
    50,
    10,
    true
),
(
    'ba000000-0000-0000-0000-000000000004',
    'b0000000-0000-0000-0000-000000000003',
    'APU-ORE-500',
    'Cuña 500g',
    500,
    3.25,
    35,
    5,
    true
)
on conflict (sku) do nothing;

-- ============================================================
-- 4. RECETAS (Sección 2 #3: Queso fresco y de hoja primero)
-- ============================================================
insert into public.recipes (
    id, slug, title, short_description, prep_minutes, servings,
    ingredients, steps, is_published
) values
(
    'd0000000-0000-0000-0000-000000000001',
    'locro-de-papa-con-queso-fresco-de-altura',
    'Locro Tradicional de Papa con Queso Fresco de Pilahuín',
    'El clásico locro andino espeso y cremoso, coronado con generoso queso fresco tierno.',
    45,
    4,
    '[
        {"item": "Papas chola peladas y cortadas", "quantity": "1 kg"},
        {"item": "Queso Fresco El Lindero cortado en cubos", "quantity": "250 g"},
        {"item": "Leche entera tibia", "quantity": "1 taza"},
        {"item": "Cebolla blanca picada finamente", "quantity": "1 tallo"},
        {"item": "Achiote en pasta o aceite", "quantity": "1 cda"},
        {"item": "Aguacate y cilantro para servir", "quantity": "al gusto"}
    ]'::jsonb,
    '[
        {"step": 1, "instruction": "Hacer un refrito en una olla mediana con el achiote y la cebolla blanca picada."},
        {"step": 2, "instruction": "Agregar las papas en trozos medianos y dorar por 3 minutos con el refrito."},
        {"step": 3, "instruction": "Cubrir con agua hirviendo y cocinar a fuego medio hasta que las papas se deshagan parcialmente y espesen el caldo."},
        {"step": 4, "instruction": "Incorporar la taza de leche tibia y dejar hervir 5 minutos más."},
        {"step": 5, "instruction": "Retirar del fuego e incorporar los cubos de queso fresco El Lindero. Servir de inmediato con aguacate."}
    ]'::jsonb,
    true
),
(
    'd0000000-0000-0000-0000-000000000002',
    'humitas-andinas-con-queso-de-hoja',
    'Humitas Dulces Rellenas de Queso de Hoja',
    'Tiernas humitas de choclo criollo con centro derretido de queso de hoja deshebrado.',
    60,
    6,
    '[
        {"item": "Choclos tiernos desgranados", "quantity": "6 unidades"},
        {"item": "Queso de Hoja Mulanleo deshebrado", "quantity": "200 g"},
        {"item": "Mantequilla derretida", "quantity": "4 cdas"},
        {"item": "Huevos (separadas claras de yemas)", "quantity": "2 unidades"},
        {"item": "Pizca de sal y azúcar", "quantity": "al gusto"},
        {"item": "Hojas de choclo limpias para envolver", "quantity": "12 hojas"}
    ]'::jsonb,
    '[
        {"step": 1, "instruction": "Moler los granos de choclo hasta obtener una masa homogénea pero con textura."},
        {"step": 2, "instruction": "Batir las claras a punto de nieve y mezclar suavemente con la masa de choclo, las yemas y la mantequilla."},
        {"step": 3, "instruction": "Colocar 2 cucharadas de masa en cada hoja de choclo y colocar una porción generosa de queso de hoja en el centro."},
        {"step": 4, "instruction": "Doblar y cerrar las humitas cuidadosamente."},
        {"step": 5, "instruction": "Cocinar al vapor en tamalera durante 40 a 45 minutos hasta que la hoja se desprenda con facilidad."}
    ]'::jsonb,
    true
)
on conflict (slug) do nothing;

-- Relación Receta -> Producto
insert into public.recipe_products (recipe_id, product_id, is_recommended) values
('d0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000001', true),
('d0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000002', true)
on conflict (recipe_id, product_id) do nothing;

-- ============================================================
-- 5. ATRACTIVOS TURÍSTICOS (Sección 2 #4 y 4.1 del documento)
-- Presentados con honestidad: experiencia rural en desarrollo
-- ============================================================
insert into public.tourist_attractions (
    id, association_id, name, attraction_type, description,
    access_conditions, latitude, longitude, requires_confirmation, is_published
) values
(
    '00000000-0000-0000-0000-000000000001',
    'a0000000-0000-0000-0000-000000000001',
    'Mirador y Pastizales de Altura El Lindero',
    'Paisaje natural andino',
    'Vista panorámica a los páramos andinos y ganado lechero de altura a 3.600 msnm. Sendero rural con aire puro de montaña.',
    'Vía secundaria de segundo orden. Acceso preferente en camioneta o vehículo particular alto. Clima frío andino; llevar ropa abrigada y calzado cerrado.',
    -1.298500, -78.712300,
    true,
    true
),
(
    '00000000-0000-0000-0000-000000000002',
    'a0000000-0000-0000-0000-000000000002',
    'Ruta del Hilado de Queso en Mulanleo',
    'Experiencia gastronómica vivencial',
    'Demostración del hilado tradicional del queso de hoja y explicación del proceso artesanal guiada por los propios socios de la quesería.',
    'Visita previa coordinación por WhatsApp con 48h de anticipación. Capacidad máxima de 8 a 10 personas por turno debido a espacio en planta.',
    -1.289100, -78.725400,
    true,
    true
)
on conflict do nothing;

-- ============================================================
-- 6. ZONAS DE ENVÍO (Sección 2 #6 y 4.7 del documento)
-- Ambato (mercado emisor principal 44,5%), Quito (potencial 17,6%)
-- ============================================================
insert into public.shipping_zones (
    id, name, description, delivery_fee, delivery_days_text, is_active
) values
(
    'f0000000-0000-0000-0000-000000000001',
    'Ambato Urbano (Puntos Céntricos y Domicilio)',
    'Entregas directas en la ciudad de Ambato y coordinación de retiro en ferias aliadas (Plaza Pachano).',
    1.50,
    'Martes, Jueves y Sábados',
    true
),
(
    'f0000000-0000-0000-0000-000000000002',
    'Retiro en Fábricas (Pilahuín)',
    'Retiro directo sin costo de envío en las queserías comunitarias asociadas a CONLAC-T.',
    0.00,
    'Lunes a Domingo (Previa coordinación)',
    true
),
(
    'f0000000-0000-0000-0000-000000000003',
    'Quito Metropolitano (Envío Refrigerado)',
    'Despacho interprovincial refrigerado hacia terminales o entregas programadas en Quito.',
    4.50,
    'Miércoles y Viernes',
    true
),
(
    'f0000000-0000-0000-0000-000000000004',
    'Guayaquil y Costa (Envío por Transporte Especializado)',
    'Envíos de pedidos consolidados y mayoristas con transporte refrigerado.',
    6.00,
    'Sábados (Salida desde Ambato)',
    true
)
on conflict (name) do nothing;

-- ============================================================
-- 7. TESTIMONIOS (Sección 2 #5: Clientes finales y B2B)
-- ============================================================
insert into public.testimonials (
    id, author_name, author_type, quote, is_authorized, is_published
) values
(
    'e0000000-0000-0000-0000-000000000001',
    'Mariana Morales',
    'Cliente Final (Ambato)',
    'El queso fresco de El Lindero sabe a la leche de campo de antes, tierno y con el punto exacto de sal. Se nota la diferencia frente al queso industrial.',
    true,
    true
),
(
    'e0000000-0000-0000-0000-000000000002',
    'Restaurante Tradiciones Andinas',
    'Comprador Comercial B2B (Quito)',
    'Compramos semanalmente el queso de hoja para nuestras humitas y empanadas. La elasticidad y el aroma de achira son insuperables para nuestros platos.',
    true,
    true
)
on conflict do nothing;

commit;
