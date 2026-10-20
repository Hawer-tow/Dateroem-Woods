CREATE USER 'dateroem_mobile'@'%' IDENTIFIED BY 'OmaqTower@6611';
GRANT SELECT, UPDATE ON dateroem.deliveries TO 'dateroem_mobile'@'%';
GRANT SELECT, INSERT ON dateroem.payments TO 'dateroem_mobile'@'%';
FLUSH PRIVILEGES;

-- Allow mobile user to see the schema
GRANT USAGE ON dateroem.* TO 'dateroem_mobile'@'%';
GRANT SELECT ON dateroem.* TO 'dateroem_mobile'@'%';

FLUSH PRIVILEGES;
