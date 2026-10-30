# Avaliação das planilhas para dashboards do Intergado

Avaliação em 25/10/2026 dos quatro arquivos em `docs/planilhas`. A leitura foi feita diretamente dos arquivos XLSX, incluindo abas, células, fórmulas e valores salvos. **Os valores de fórmulas são caches do Excel; não foram recalculados nesta avaliação.** Os números abaixo descrevem os arquivos recebidos e não representam necessariamente a situação atual das fazendas.

## Resumo

| Arquivo | Conteúdo | Tamanho lógico | Uso recomendado |
| --- | --- | ---: | --- |
| `01_Controle_gado.xlsx` | Movimentações e saldos em Santa Cruz, Ilha Verde, Novo Mundo e Campo Verde | 4 abas; 576 linhas preenchidas | Dashboard **histórico** do rebanho e conciliação |
| `01_Fichas_rotacionado_SC_M_JUL_2026.xlsx` | 15 módulos de pastejo da Santa Cruz, calendário, manejo, evolução e animais parados | 20 abas; 8.747 linhas preenchidas; 35.934 fórmulas | Dashboard de manejo após separar observações de projeções |
| `01_Rendimento_bois_da_ração.xlsx` | Lotes de alimentação em Ilha Verde, Novo Mundo e Santa Cruz | 3 abas; 21 linhas preenchidas | Dashboard de ganho de peso após revisar lotes incompletos |
| `01_Aplicação_PT_SANTA_CRUZ_2024-25.xlsx` | Área por pasto e quantidades calculadas de três produtos | 1 aba; 32 pastos | Dashboard de **planejamento** de insumos |

### Conclusão prática

O melhor primeiro painel é **manejo da Santa Cruz**, seguido de **histórico de movimentações do gado**. Há dados suficientes para mostrar ocupação, carga por hectare, calendário e fluxos de animais. A importação precisa distinguir registros feitos por pessoas, valores derivados por fórmulas e datas planejadas. As planilhas não sustentam, sozinhas, um painel confiável de estoque atual, custos de produção ou rentabilidade.

## 1. Controle de gado

As quatro abas têm uma tabela principal com `DATA`, `DESCRIÇÃO`, `COMPRA`, `Morte`, `Consumo`, `VENDA`, `SALDO`, `Saldo Adepará` e `Saldo Fiscal` (ex.: `Santa Cruz!A1:I1`). O saldo da coluna G é acumulado por fórmula; por exemplo, `Santa Cruz!G3 = G2+C3-D3-E3-F3`.

| Fazenda | Último lançamento datado | Saldo G no lançamento | Saldo Adepará H |
| --- | --- | ---: | ---: |
| Santa Cruz | 25/02/2021 | 2.881 | 2.676 |
| Ilha Verde | 15/02/2021 | 3.985 | 3.337 |
| Novo Mundo | 10/03/2021 | 2.271 | 2.617 |
| Campo Verde | 17/03/2021 | 923 | 895 |

Esses saldos são **históricos** e têm datas diferentes. O significado operacional da diferença entre G e H deve ser confirmado antes de chamá-la de divergência. `Saldo Fiscal` aparece no cabeçalho, mas não está preenchido nos últimos lançamentos verificados.

**Qualidade e regras de importação:**

- Há movimentações sem data: 9 em Santa Cruz, 2 em Ilha Verde, 5 na tabela principal de Novo Mundo e 1 em Campo Verde. Elas não devem receber uma data inventada automaticamente.
- Existem datas fora de ordem; `Ilha Verde!A57 = 40024` equivale a 30/07/2009, no meio de lançamentos de 2019, provável erro de digitação a revisar.
- A coluna `COMPRA` contém também eventos descritos como nascimento ou desmama. O tipo da movimentação deve ser classificado pela descrição e confirmado, não inferido apenas do título da coluna.
- Novo Mundo contém quadros laterais e listas de animais de serviço fora da tabela principal. Essas células não são movimentações de gado.
- No trecho em que há saldo anterior e quantidades numéricas nas colunas C a F, a aritmética do saldo G conferiu; isso não valida a data, a natureza da operação nem a conciliação com Adepará.

**Dashboards viáveis:** evolução histórica do saldo por fazenda; entradas, vendas, mortes e consumo por mês; taxa de mortalidade com denominador definido; lista de eventos sem data; comparação de saldos internos e Adepará com contexto de reconciliação.

## 2. Fichas de manejo rotacionado da Santa Cruz

As 15 abas `S.C.M1` a `S.C.M15` combinam cadastro de pastos, área, área efetiva, capim, peso, número de cabeças, capacidade calculada e histórico/calendário de entrada e saída. Foram identificadas **cerca de 7.704 linhas candidatas** a períodos de pastejo nas tabelas dos módulos, de 2010 a 2026. A aba `MANEJO` contém um resumo dos 15 módulos, mas é quase toda composta de referências e fórmulas; por exemplo, `MANEJO!F2` busca o valor de outra aba.

A aba `Calendário SC` tem 189 linhas com módulo, piquete e entrada, de 24/06/2026 a 30/09/2026. As células são **fórmulas que referenciam outras abas** (`Calendário SC!A4:C4`), portanto não devem ser importadas de novo como novos eventos. A aba `EVOLUÇÃO` projeta pesos por mês e mistura módulos, pastos e totais. A aba `PARADO` tem quadros históricos laterais e **23 células com erro `#REF!`**; deve ficar fora da primeira importação automática.

**Risco central:** `S.C. M1 - 989!G7` contém `TODAY()` com valor salvo de **27/08/2026**, embora esta avaliação seja de 25/10/2026. Assim, os resultados calculados em cache podem estar desatualizados. Há ainda datas de saída posteriores a outubro de 2026 em algumas fichas. Uma data futura ou calculada precisa de um campo `planejado/realizado`; a data sozinha não prova que o manejo aconteceu.

**Dashboards viáveis após validação:** mapa de ocupação por módulo/pasto; cabeças e peso vivo por hectare versus carga ideal; módulos acima/abaixo da capacidade; dias de ocupação e descanso; calendário de próximas mudanças; histórico de ciclos por piquete. Use os registros de base dos módulos como fonte e recalcule indicadores no backend, mantendo o valor original apenas para auditoria.

## 3. Rendimento dos bois na ração

As abas de Ilha Verde e Novo Mundo usam, em geral, pasto, data e quantidade de entrada, peso de entrada, início da ração, peso estimado, saída, quantidade de saída, peso de saída, ganho diário e rendimento (`Ilha Verde!A1:Q1`). Santa Cruz usa uma estrutura semelhante, mas com colunas deslocadas e dados ausentes.

- Há 14 linhas com campos principais de entrada e saída preenchidos em Ilha Verde e Novo Mundo, 2 linhas parciais em Santa Cruz e outras linhas parciais em Novo Mundo.
- Um lote pode ter saídas em duas etapas: `Novo Mundo!H4` registra 8 animais e `M4` mais 12, somando os 20 de entrada. O modelo deve aceitar **múltiplas saídas por lote**.
- Em Ilha Verde, três linhas têm quantidade de saída principal menor que a de entrada sem uma segunda saída registrada (`linhas 4 a 6`). Isso pode representar perda, permanência ou registro incompleto; exige conciliação.
- A fórmula do ganho diário muda entre abas: `Ilha Verde!J2 = (I2-F2)/(G2-E2)`, enquanto `Santa Cruz!J2 = (I2-D2)/(G2-B2)`. Uma usa peso estimado e início da ração; a outra, peso/data de entrada. Não combine as taxas sem padronizar a definição.
- `RENDIMENTO` apresenta valores em torno de 0,55, mas não há definição suficiente para afirmar que representa conversão alimentar, rendimento de carcaça ou outra medida.

**Dashboards viáveis:** lotes por fazenda/pasto, animais em alimentação, tempo de permanência, peso de entrada/saída e ganho médio diário **com metodologia padronizada**, além de pendências de quantidade não conciliada. Custo por cabeça e conversão alimentar exigem consumo de ração e preços, que não aparecem nessas planilhas.

## 4. Planejamento de aplicação de produtos

Há 32 pastos da Santa Cruz nas linhas 4 a 35 e área total calculada de **719,19 ha** (`Planilha1!B44`). As colunas C, D e E calculam quantidades a partir da área: por exemplo, `C4 = B4*3,5`, `D4 = B4*0,5` e `E4 = B4*0,1`. Os totais salvos são 2.517,165; 359,595; e 71,919, respectivamente.

O título interno diz **“APLICAÇÃO CAPIM CAPETA 2024”**, embora o nome do arquivo inclua `2024-25`. Não há data por pasto, registro de execução, unidade explícita dos produtos, preço nem lote comprado. `COMPRADO` não tem quantidades preenchidas. Portanto, estes dados representam **necessidade/planejamento calculado**, não aplicação efetiva nem consumo de estoque.

**Dashboard viável:** área planejada por pasto, necessidade estimada por produto e comparação com pedidos de compra depois de cadastrar unidades, produtos e quantidades compradas. Séries de aplicação por data não são possíveis com este arquivo.

## Modelo de dados relacional recomendado

| Entidade | Campos essenciais | Origem |
| --- | --- | --- |
| `farm` | id, nome canônico, aliases | Abas e títulos |
| `pasture` | id, farm_id, código canônico, aliases, área_ha | Aplicação e fichas |
| `module` | id, farm_id, número | Fichas de manejo |
| `herd_movement` | farm_id, data nullable, tipo, quantidade, descrição, saldo_original, linha_fonte | Controle de gado |
| `grazing_cycle` | module_id, pasture_id, entrada, saída, dias, situação (`PLANEJADO`/`REALIZADO`/`INDEFINIDO`) | Fichas dos módulos |
| `capacity_snapshot` | module_id, data_referência, área_efetiva, cabeças, peso_médio, kg_ha, kg_ha_ideal | Fichas/Manejo após recálculo |
| `feeding_lot` e `feeding_exit` | fazenda, pasto, datas, cabeças, pesos e múltiplas saídas | Rendimento da ração |
| `treatment_plan_item` | pasto, campanha, produto, dose, unidade, área, quantidade_prevista | Aplicação |
| `import_batch` e `source_row` | arquivo, hash, aba, célula/linha, data de importação, valor original, erro de validação | Todos |

O identificador de pasto precisa preservar distinções como `49 Frente` e `49 Fundo`, ao mesmo tempo em que normaliza variações de escrita como `60A`/`60 A` e `PASTO 05`/`5`. A relação entre pasto e módulo pode mudar no tempo. A importação deve ser idempotente por hash do arquivo e chave da linha, preservar a célula de origem e guardar registros recusados para revisão.

Na aplicação atual, o Spring Boot pode receber e validar a importação, persistir os fatos no SQLite da demonstração e expor os eventos; o Quarkus pode agregar os novos indicadores; o React pode apresentar os painéis. As tabelas atuais de compras usam `farm` como texto livre, portanto a integração entre compras, pastos e insumos exige primeiro os cadastros canônicos de fazenda, pasto e produto. Para uma implantação com muitos usuários e gravações simultâneas, pode-se migrar esse modelo para PostgreSQL.

## Ordem sugerida

1. **Cadastros e importação auditável:** fazenda, pasto, módulo, lote de importação, conversão de datas Excel e relatório de erros.
2. **Gado histórico:** importar as quatro tabelas principais, revisar datas ausentes/anômalas e publicar saldo e movimentos com aviso de período histórico.
3. **Manejo Santa Cruz:** importar cadastros e ciclos dos módulos; marcar planejado/realizado e recalcular carga e descanso. Não duplicar a aba `Calendário SC`.
4. **Ração:** padronizar a fórmula de ganho e conciliar entradas/saídas de lotes antes de agregar resultados.
5. **Aplicação e compras:** confirmar unidades/doses e associar planos de aplicação às solicitações de compra. Só apresentar aplicação realizada quando houver registros de execução.

### Decisões de negócio pendentes

- Quem confirma se cada saída/entrada do rotacionado foi realizada ou apenas planejada?
- Qual a definição e unidade de `RENDIMENTO` na ração? Qual peso/data deve iniciar o cálculo do ganho diário?
- O que representa cada diferença entre saldo interno e Adepará? Há dados de 2021 em diante em outra fonte?
- Quais são as unidades dos produtos da planilha de aplicação e a data real de cada aplicação?
- Existe um identificador oficial de pasto/módulo que permita unir as quatro fontes sem ambiguidade?
