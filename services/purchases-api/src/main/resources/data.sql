INSERT OR IGNORE INTO purchase_requests (id, title, farm, category, requester, supplier, amount, status, requested_at, demo) VALUES
(1, 'Peças para pivô central', 'Santa Cruz', 'Manutenção', 'Equipe de campo', 'Agro Peças', 12850.00, 'AWAITING_APPROVAL', strftime('%Y-%m-%dT%H:%M:%fZ', 'now', '-1 day'), 1),
(2, 'Ração para confinamento', 'Santa Cruz', 'Pecuária', 'Gerência', 'NutriCampo', 18400.00, 'ORDERED', strftime('%Y-%m-%dT%H:%M:%fZ', 'now', '-2 days'), 1),
(3, 'Fertilizante para pastagem', 'Ilha Verde', 'Insumos', 'Agrônomo', 'Campo Forte', 9200.00, 'QUOTING', strftime('%Y-%m-%dT%H:%M:%fZ', 'now', '-3 days'), 1),
(4, 'Bomba hidráulica', 'Santa Cruz', 'Manutenção', 'Equipe de campo', 'Hidro Rural', 4350.00, 'DELIVERED', strftime('%Y-%m-%dT%H:%M:%fZ', 'now', '-5 days'), 1),
(5, 'Vacinas do rebanho', 'Novo Mundo', 'Pecuária', 'Gerência', 'Vet Agro', 6700.00, 'PAID', strftime('%Y-%m-%dT%H:%M:%fZ', 'now', '-8 days'), 1),
(6, 'Óleo diesel', 'Santa Cruz', 'Combustível', 'Equipe de campo', NULL, 0, 'RECEIVED', strftime('%Y-%m-%dT%H:%M:%fZ', 'now', '-12 hours'), 1),
(7, 'Correias da colheitadeira', 'Campo Verde', 'Manutenção', 'Gerência', 'Agro Peças', 3150.00, 'APPROVED', strftime('%Y-%m-%dT%H:%M:%fZ', 'now', '-4 days'), 1);

INSERT OR IGNORE INTO farms (id, name, balance_date, herd_balance, adepara_balance, sources, demo) VALUES
(1, 'Santa Cruz', '2021-02-25', 2881, 2676, 'Controle de gado, Manejo rotacionado, Ração, Planejamento de insumos', 1),
(2, 'Ilha Verde', '2021-02-15', 3985, 3337, 'Controle de gado, Ração', 1),
(3, 'Novo Mundo', '2021-03-10', 2271, 2617, 'Controle de gado, Ração', 1),
(4, 'Campo Verde', '2021-03-17', 923, 895, 'Controle de gado', 1);
