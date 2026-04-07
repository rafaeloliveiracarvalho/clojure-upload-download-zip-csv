# API de Upload/Download de Arquivos CSV

API em Clojure para gerenciamento de arquivos CSV com armazenamento em S3. Projetada para ser eficiente com arquivos grandes, utilizando streaming de dados e processamento em lote.

## Stack Tecnológico

| Componente | Tecnologia |
|------------|------------|
| **Linguagem** | Clojure 1.12 |
| **Web Framework** | Reitit (roteamento) + Ring/Jetty (servidor) |
| **Banco de Dados** | PostgreSQL (next.jdbc) |
| **Armazenamento** | S3Proxy (AWS S3 local) |
| **Frontend** | re-frame + shadow-cljs + Bulma |
| **Logging** | Timbre |

## Funcionalidades

- **Upload de arquivos CSV**: Arquivos são compactados em ZIP e armazenados no S3
- **Download de arquivos**: Descompactação automática do ZIP, retornando o CSV original
- **Listagem de arquivos**: Metadados de todos os arquivos enviados
- **Geração de relatórios**: Relatório CSV com todos os arquivos (suporta 1M+ linhas via batch processing)
- **Streaming**: Operações com memória constante, independente do tamanho do arquivo

## Arquitetura

### Estrutura de Namespaces

```
clojure-download-zip-csv/
├── api/src/clojure_download_zip_csv/
│   ├── core.clj       # Ponto de entrada, inicialização
│   ├── routes.clj     # Definição de rotas Reitit
│   ├── handler.clj    # Handlers HTTP (upload, download, list, report)
│   ├── service.clj    # Lógica de negócio
│   ├── db.clj         # Acesso ao PostgreSQL
│   └── s3.clj         # Operações com S3
├── front/src/clojure_download_zip_csv/frontend/
│   ├── core.cljs      # inicialização re-frame
│   ├── events.cljs    # Eventos e efeitos
│   ├── subs.cljs      # Subscribe/Estado
│   └── views.cljs     # Componentes UI
└── docs/
    └── service.md     # Documentação detalhada do service.clj
```

### Decisões Técnicas

- **Streaming para Upload**: Arquivo temporário → ZIP → S3 (memória constante)
- **Streaming para Download**: S3 → ZIP → PipedInputStream → Response (buffer 8KB)
- **Batch Processing para Relatórios**: 10.000 registros por vez (evita estouro de memória)
- **Separação de Responsabilidades**: Cada namespace tem função específica

## Configuração

### Variáveis de Ambiente

| Variável | Padrão | Descrição |
|----------|--------|-----------|
| `DB_HOST` | localhost | Host do PostgreSQL |
| `DB_PORT` | 5432 | Porta do PostgreSQL |
| `DB_NAME` | files_db | Nome do banco |
| `DB_USER` | postgres | Usuário PostgreSQL |
| `DB_PASSWORD` | postgres | Senha PostgreSQL |
| `S3_ENDPOINT` | http://127.0.0.1:8001 | Endpoint do S3 |

### Docker Compose

O projeto utiliza Docker Compose para desenvolvimento local:

```yaml
services:
  db:
    image: postgres:16-alpine
    
  s3:
    image: andrewgaul/s3proxy
    environment:
      - S3PROXY_AUTHORIZATION=none
      
  api:
    build: ./api
    
  front:
    build: ./front
```

## Endpoints da API

| Método | Endpoint | Descrição | Corpo/Params |
|--------|----------|-----------|--------------|
| POST | `/api/upload` | Upload de arquivo CSV | multipart/form-data |
| GET | `/api/files` | Lista todos os arquivos | - |
| GET | `/api/files/report` | Gera relatório CSV | - |
| GET | `/api/download/:id` | Download arquivo | path param |

### Exemplos de Uso

**Upload de arquivo:**
```bash
curl -X POST -F "file=@dados.csv" http://localhost:3000/api/upload
```

**Listar arquivos:**
```bash
curl http://localhost:3000/api/files
```

**Gerar relatório:**
```bash
curl http://localhost:3000/api/files/report
```

**Download de arquivo:**
```bash
curl -o arquivo.csv "http://localhost:3000/api/download/{id}"
```

## Como Rodar

### Pré-requisitos

- Docker/Podman
- Docker Compose

### Executar

```bash
# Iniciar todos os serviços
podman compose up --build

# Acessar a aplicação
# Frontend: http://localhost:3001
# API: http://localhost:3000
```

## Estrutura do Banco de Dados

### Tabela: files

| Coluna | Tipo | Descrição |
|--------|------|-----------|
| id | UUID | PK - Identificador único |
| original_name | VARCHAR(255) | Nome original do arquivo |
| upload_date | VARCHAR(20) | Data/hora do upload (formatado) |
| upload_timestamp | BIGINT | Timestamp Unix do upload |
| s3_key | VARCHAR(255) | Chave do arquivo no S3 |
| file_type | VARCHAR(20) | Tipo: "upload" ou "relatorio" |

## Tratamento de Erros

A API retorna respostas consistentes em caso de erros:

```clojure
;; Sucesso
{:status :success :id "uuid" :filename "arquivo.csv"}

;; Erro
{:status :error :message "Descrição do erro"}
```

Códigos de status HTTP:
- 200/201: Sucesso
- 400: Erro de validação (ex: arquivo não é CSV)
- 404: Arquivo não encontrado
- 500: Erro interno (banco, S3, etc)

## Documentação Adicional

- [Documentação detalhada do service.clj](docs/service.md) - Explicação linha a linha de todas as funções

## Licença

EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0