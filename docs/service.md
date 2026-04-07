# Documentação do Módulo de Serviços (`service.clj`)

Este documento fornece uma explicação detalhada, linha a linha, de cada função do módulo de serviços.

---

## Visão Geral

O arquivo `service.clj` contém a lógica de negócio principal da aplicação, implementando as operações de:
- Upload de arquivos CSV
- Download de arquivos (descompactando ZIP)
- Geração de relatórios em lote
- Listagem de arquivos

Todas as operações utilizam streaming para garantir eficiência com arquivos grandes.

---

## Imports e Namespace

```clojure
(ns clojure-download-zip-csv.service
  (:require [clojure-download-zip-csv.db :as db]
            [clojure-download-zip-csv.s3 :as s3]
            [clojure.java.io :as io]
            [taoensso.timbre :as timbre])
  (:import [java.time LocalDateTime]
           [java.time.format DateTimeFormatter]
           [java.util UUID]
           [java.io File BufferedWriter OutputStreamWriter PipedInputStream PipedOutputStream]
           [java.util.zip ZipInputStream ZipOutputStream ZipEntry]))
```

### Explicação dos Imports

| Import | Propósito |
|--------|-----------|
| `clojure-download-zip-csv.db` | Acesso ao banco de dados PostgreSQL |
| `clojure-download-zip-csv.s3` | Operações com S3 (upload, download) |
| `clojure.java.io` | Operações de I/O (arquivos, streams) |
| `taoensso.timbre` | Logging/auditoria |
| `LocalDateTime` / `DateTimeFormatter` | Formatação de datas |
| `UUID` | Geração de IDs únicos |
| `File` | Manipulação de arquivos temporários |
| `BufferedWriter` / `OutputStreamWriter` | Escrita de CSV |
| `PipedInputStream` / `PipedOutputStream` | Streams para streaming em tempo real |
| `ZipInputStream` / `ZipOutputStream` / `ZipEntry` | Compactação/descompactação ZIP |

---

## Função: process-upload!

**Propósito**: Processa upload de arquivos CSV, compactando-os em ZIP e armazenando no S3.

```clojure
(defn process-upload! [file]
```

### Linha a Linha

**Linha 13** - Validação do arquivo:
```clojure
(if (and file (.endsWith (.toLowerCase (:filename file)) ".csv"))
```
- Verifica se o arquivo existe
- Converte nome para lowercase
- Verifica se a extensão é `.csv`
- Retorna erro se não for CSV

**Linha 14-21** - Preparação de variáveis:
```clojure
(let [id (UUID/randomUUID)
      filename (:filename file)
      temp-file (:tempfile file)
      s3-key (str id "-" filename ".zip")
      zip-file (io/file (str "/tmp/" s3-key))
      now (LocalDateTime/now)
      date-str (.format now (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss"))
      timestamp (System/currentTimeMillis)]
```

| Variável | Descrição |
|----------|-----------|
| `id` | UUID único para o arquivo |
| `filename` | Nome original do arquivo |
| `temp-file` | Arquivo temporário do upload |
| `s3-key` | Chave única no S3 (`uuid-nome.zip`) |
| `zip-file` | Caminho do arquivo ZIP temporário |
| `now` | Data/hora atual |
| `date-str` | Data formatada para o banco |
| `timestamp` | Timestamp em milissegundos |

**Linha 23-27** - Criação do ZIP com streaming:
```clojure
(try
  (with-open [zip-out (ZipOutputStream. (io/output-stream zip-file))]
    (.putNextEntry zip-out (ZipEntry. filename))
    (io/copy temp-file zip-out)
    (.closeEntry zip-out))
```

| Passo | Descrição |
|-------|-----------|
| `ZipOutputStream` | Cria stream para escrever ZIP |
| `.putNextEntry` | Adiciona entrada no ZIP com o nome do arquivo |
| `io/copy` | Copia conteúdo do CSV para o ZIP (streaming) |
| `.closeEntry` | Finaliza entrada do ZIP |
| `with-open` | Garante fechamento automático |

**Linha 29-32** - Upload para S3:
```clojure
(let [zip-size (.length zip-file)
      zip-stream (io/input-stream zip-file)]
  (s3/upload-stream! s3-key zip-stream zip-size)
  (.close zip-stream))
```

| Passo | Descrição |
|-------|-----------|
| `.length` | Calcula tamanho do ZIP |
| `upload-stream!` | Envia stream para S3 com content-length |
| `.close` | Fecha stream após upload |

**Linha 34-38** - Salvamento de metadados:
```clojure
(db/save-file-metadata! {:id id 
                          :original_name filename 
                          :upload_date date-str 
                          :upload_timestamp timestamp 
                          :s3_key s3-key})
```

| Campo | Descrição |
|-------|-----------|
| `:id` | UUID único |
| `:original_name` | Nome original do arquivo |
| `:upload_date` | Data formatada |
| `:upload_timestamp` | Timestamp Unix |
| `:s3_key` | Chave no S3 |

**Linha 40** - Limpeza de arquivo temporário:
```clojure
(io/delete-file zip-file)
```

**Linha 41** - Retorno de sucesso:
```clojure
{:status :success :id id}
```

**Linha 43-45** - Tratamento de erros:
```clojure
(catch Exception e
  (io/delete-file zip-file)
  {:status :error :message (.getMessage e)})
```

**Linha 46** - Retorno de erro (validação):
```clojure
{:status :error :message "Apenas arquivos CSV são permitidos"})
```

---

## Função: list-files

**Propósito**: Lista todos os arquivos do banco de dados.

```clojure
(defn list-files []
  (db/find-all-files))
```

 Simplesmente delega para a função `find-all-files` em `db.clj`, que executa:
```sql
SELECT id, original_name, upload_date, upload_timestamp, file_type FROM files
```

**Retorno**: Vetor de mapas com metadados dos arquivos.

---

## Função: download-file

**Propósito**: Baixa arquivo do S3, descompacta do ZIP e retorna como stream.

```clojure
(defn download-file [id-str]
```

### Linha a Linha

**Linha 52-53** - Parsing do ID:
```clojure
(let [id (try (UUID/fromString id-str) (catch Exception _ nil))
      file-record (when id (db/find-file-by-id id))]
```

| Passo | Descrição |
|-------|-----------|
| `UUID/fromString` | Converte string para UUID |
| `catch` | Retorna `nil` se string inválida |
| `db/find-file-by-id` | Busca metadados no banco |

**Linha 54-57** - Verificação e extração de dados:
```clojure
(if file-record
  (let [s3-key (:files/s3_key file-record)
        original-name (:files/original_name file-record)
        filename (clojure.string/replace original-name #"\.zip$" ".csv")]
```

| Variável | Descrição |
|----------|-----------|
| `s3-key` | Chave do arquivo no S3 |
| `original-name` | Nome original (pode ter extensão `.zip`) |
| `filename` | Normaliza extensão para `.csv` |

**Linha 58-61** - Busca arquivo no S3:
```clojure
(try
  (let [s3-stream (s3/get-file-stream s3-key)
        zis (ZipInputStream. s3-stream)
        entry (.getNextEntry zis)]
```

| Passo | Descrição |
|-----|-----------|
| `s3/get-file-stream` | Busca arquivo no S3 (retorna InputStream) |
| `ZipInputStream` | Cria stream para descompactar ZIP |
| `.getNextEntry` | Lê próxima entrada do ZIP |

**Linha 62-64** - Verificação da entrada:
```clojure
(if entry
  (let [piped-out (PipedOutputStream.)
        piped-in (PipedInputStream. piped-out 65536)]
```

| Componente | Descrição |
|------------|-----------|
| `PipedOutputStream` | Stream onde serão escritos os dados descompactados |
| `PipedInputStream` | Stream de onde o cliente lerá os dados (65536 = buffer) |

**Linha 65-78** - Thread para processamento:
```clojure
(future
  (try
    (let [buffer (byte-array 8192)]
      (loop []
        (let [len (.read zis buffer 0 (alength buffer))]
          (when (> len 0)
            (.write piped-out buffer 0 len)
            (recur))))
    (.closeEntry zis))
  (catch Exception e
    (timbre/error "Download pipeline error:" e))
  (finally
    (try (.close zis) (catch Exception _))
    (try (.close piped-out) (catch Exception _))))
```

| Parte | Descrição |
|-------|-----------|
| `future` | Executa processamento em thread separada |
| `buffer` | Array de 8KB para leitura chunkada |
| `loop/recur` | Lê do ZIP até EOF (-1) |
| `.read` | Lê bytes do ZIP |
| `.write` | Escreve bytes no pipe |
| `catch` | Log de erros |
| `finally` | Garante fechamento dos streams |

**Linha 79-81** - Retorno do stream:
```clojure
{:status :success 
 :stream piped-in 
 :filename filename}
```

| Campo | Descrição |
|-------|-----------|
| `:stream` | PipedInputStream para a response HTTP |
| `:filename` | Nome do arquivo para headers |

**Linha 82-84** - Arquivo ZIP inválido:
```clojure
(do
  (.close zis)
  {:status :error :message "Arquivo ZIP inválido ou vazio"})
```

**Linha 85-86** - Erro no S3:
```clojure
(catch Exception e
  {:status :error :message "Erro ao buscar arquivo no S3"})
```

**Linha 87** - Arquivo não encontrado:
```clojure
{:status :error :message "Arquivo não encontrado"})
```

---

## Funções Auxiliares de CSV

### row-to-csv-line

**Propósito**: Converte um registro do banco em uma linha de CSV.

```clojure
(defn- row-to-csv-line [row]
  (let [id (or (get row :files/id) (get row :id) "")
        original-name (or (get row :files/original_name) (get row :original_name) "")
        upload-date (or (get row :files/upload_date) (get row :upload_date) "")
        upload-timestamp (or (get row :files/upload_timestamp) (get row :upload_timestamp) "")
        s3-key (or (get row :files/s3_key) (get row :s3_key) "")
        file-type (or (get row :files/file_type) (get row :file_type) "upload")]
    (str id "," original-name "," upload-date "," upload-timestamp "," s3-key "," file-type)))
```

| Campo | Fonte |
|-------|-------|
| `id` | `:files/id` ou `:id` |
| `original_name` | `:files/original_name` ou `:original_name` |
| `upload_date` | `:files/upload_date` ou `:upload_date` |
| `upload_timestamp` | `:files/upload_timestamp` ou `:upload_timestamp` |
| `s3_key` | `:files/s3_key` ou `:s3_key` |
| `file_type` | `:files/file_type` ou `:file_type` (default "upload") |

**Nota**: Usa `or` para suportar ambos os formatos de chave (com e sem namespace).

---

### write-csv-header

**Propósito**: Escreve o cabeçalho do arquivo CSV.

```clojure
(defn- write-csv-header [writer]
  (.write writer "id,original_name,upload_date,upload_timestamp,s3_key,file_type\n"))
```

---

### write-csv-row

**Propósito**: Escreve uma linha de dados no CSV.

```clojure
(defn- write-csv-row [writer row]
  (.write writer (row-to-csv-line row))
  (.newLine writer))
```

---

## Variável: batch-size

**Propósito**: Define o tamanho do lote para processamento de relatórios.

```clojure
(def batch-size 10000)
```

Valor definido: **10.000 registros** por batch.

---

## Função: generate-report!

**Propósito**: Gera relatório CSV com todos os arquivos do banco, compactado em ZIP e enviado ao S3. Suporta milhões de linhas usando processamento em lote.

```clojure
(defn generate-report! []
```

### Linha a Linha

**Linha 108** - Contagem total:
```clojure
(let [total-count (db/count-files)
```

Chama `db/count-files` para obter o total de registros (necessário para a resposta).

**Linha 109-117** - Preparação de variáveis:
```clojure
      id (UUID/randomUUID)
      timestamp (System/currentTimeMillis)
      filename (str timestamp "_relatorio.csv")
      zip-filename (str filename ".zip")
      s3-key (str id "-" zip-filename)
      zip-file (io/file (str "/tmp/" zip-filename))
      csv-file (io/file (str "/tmp/" filename))
      now (LocalDateTime/now)
      date-str (.format now (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss"))]
```

| Variável | Descrição |
|----------|-----------|
| `id` | UUID único para o relatório |
| `timestamp` | Timestamp Unix |
| `filename` | Nome do CSV (`timestamp_relatorio.csv`) |
| `zip-filename` | Nome do ZIP (`filename.zip`) |
| `s3-key` | Chave no S3 |
| `zip-file` | Arquivo temporário do ZIP |
| `csv-file` | Arquivo temporário do CSV |

**Linha 119-127** - Geração do CSV com batch processing:
```clojure
(try
  (with-open [writer (BufferedWriter. (OutputStreamWriter. (io/output-stream csv-file) "UTF-8"))]
    (write-csv-header writer)
    (loop [offset 0]
      (let [batch (db/find-files-batch offset batch-size)]
        (when (seq batch)
          (doseq [row batch]
            (write-csv-row writer row))
          (recur (+ offset batch-size))))))
```

| Passo | Descrição |
|-------|-----------|
| `BufferedWriter` | Escrita bufferizada (melhor performance) |
| `write-csv-header` | Escreve cabeçalho |
| `loop [offset 0]` | Inicia loop com offset 0 |
| `db/find-files-batch` | Busca 10k registros a partir do offset |
| `when (seq batch)` | Verifica se há registros |
| `doseq [row batch]` | Escreve cada linha no CSV |
| `recur` | Continua para o próximo batch |

**Linha 129-132** - Criação do ZIP:
```clojure
(with-open [zip-out (ZipOutputStream. (io/output-stream zip-file))]
  (.putNextEntry zip-out (ZipEntry. filename))
  (io/copy csv-file zip-out)
  (.closeEntry zip-out))
```

Mesma lógica do `process-upload!`.

**Linha 134-137** - Upload para S3:
```clojure
(let [zip-size (.length zip-file)
      zip-stream (io/input-stream zip-file)]
  (s3/upload-stream! s3-key zip-stream zip-size)
  (.close zip-stream))
```

Mesma lógica do `process-upload!`.

**Linha 139-144** - Salvamento de metadados:
```clojure
(db/save-file-metadata! {:id id
                         :original_name (str timestamp "_relatorio.zip")
                         :upload_date date-str
                         :upload_timestamp timestamp
                         :s3_key s3-key
                         :file_type "relatorio"})
```

| Campo | Valor |
|-------|-------|
| `:file_type` | `"relatorio"` (区别 de uploads) |

**Linha 146-147** - Limpeza:
```clojure
(io/delete-file csv-file)
(io/delete-file zip-file)
```

Remove ambos arquivos temporários.

**Linha 149** - Retorno:
```clojure
{:status :success :id id :filename (str timestamp "_relatorio.zip") :total-records total-count}
```

**Linha 151-154** - Tratamento de erros:
```clojure
(catch Exception e
  (io/delete-file csv-file)
  (io/delete-file zip-file)
  {:status :error :message (.getMessage e)})
```

---

## Resumo de Arquitetura

### Streaming

| Operacao | Abordagem | Memoria |
|----------|-----------|----------|
| Upload | Arquivo temporário → ZIP → S3 stream | Constante (buffer) |
| Download | S3 stream → ZIP → Piped stream → Response | Constante (8KB) |
| Relatorio | Batch 10k → CSV → ZIP → S3 stream | 10k rows por vez |

### Tratamento de Erros

Todas as funções principais (`process-upload!`, `download-file`, `generate-report!`) possuem:
- Bloco `try/catch` para tratamento de exceções
- Limpeza de recursos (arquivos, streams)
- Retorno de mapa com `:status` e `:message` ou `:error`

### Logging

A função `download-file` usa `timbre/error` para logging de erros no pipeline de download:
```clojure
(timbre/error "Download pipeline error:" e)
```