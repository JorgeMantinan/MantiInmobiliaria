-- V8__inmuebles_real_estate_images.sql
-- Sustituye los placeholders aleatorios (picsum) del seed por fotos reales de
-- inmuebles (Unsplash, licencia libre). El mapeo es por id % N para que funcione
-- igual en dev (seed V900) y en producción (las 35 filas sembradas con el mismo
-- orden comparten ids 1..35). Idempotente: UPDATE puro.
UPDATE inmuebles SET imagen_url = CASE (id - 1) % 10
    WHEN 0 THEN 'https://images.unsplash.com/photo-1568605114967-8130f3a36994?auto=format&fit=crop&w=800&h=600&q=80'
    WHEN 1 THEN 'https://images.unsplash.com/photo-1560448204-e02f11c3d0e2?auto=format&fit=crop&w=800&h=600&q=80'
    WHEN 2 THEN 'https://images.unsplash.com/photo-1502672260266-1c1ef2d93688?auto=format&fit=crop&w=800&h=600&q=80'
    WHEN 3 THEN 'https://images.unsplash.com/photo-1493809842364-78817add7ffb?auto=format&fit=crop&w=800&h=600&q=80'
    WHEN 4 THEN 'https://images.unsplash.com/photo-1484154218962-a197022b5858?auto=format&fit=crop&w=800&h=600&q=80'
    WHEN 5 THEN 'https://images.unsplash.com/photo-1512917774080-9991f1c4c750?auto=format&fit=crop&w=800&h=600&q=80'
    WHEN 6 THEN 'https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?auto=format&fit=crop&w=800&h=600&q=80'
    WHEN 7 THEN 'https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=800&h=600&q=80'
    WHEN 8 THEN 'https://images.unsplash.com/photo-1600607687939-ce8a6c25118c?auto=format&fit=crop&w=800&h=600&q=80'
    ELSE 'https://images.unsplash.com/photo-1600566753086-00f18fb6b3ea?auto=format&fit=crop&w=800&h=600&q=80'
END
WHERE id BETWEEN 1 AND 35;
