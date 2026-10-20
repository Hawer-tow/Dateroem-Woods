CREATE USER 'dateroem_frontend'@'%' IDENTIFIED BY 'OmaqTower@6622';
GRANT SELECT, INSERT, UPDATE ON dateroem.orders TO 'dateroem_frontend'@'%';
GRANT SELECT ON dateroem.products TO 'dateroem_frontend'@'%';
GRANT SELECT, INSERT ON dateroem.payments TO 'dateroem_frontend'@'%';
FLUSH PRIVILEGES;
-- Allow frontend user to see the schema
GRANT USAGE ON dateroem.* TO 'dateroem_frontend'@'%';
GRANT SELECT ON dateroem.* TO 'dateroem_frontend'@'%';

FLUSH PRIVILEGES;