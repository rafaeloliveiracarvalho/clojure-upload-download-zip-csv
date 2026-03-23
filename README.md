# 🛡️ Clojure S3 Download API

Esta API em Clojure gerencia o download de arquivos CSV zipados armazenados no AWS S3. Ela foi projetada para ser eficiente, utilizando streaming de dados para evitar consumo excessivo de memória RAM.

## 🚀 Tecnologias

- **Linguagem**: Clojure (Gerenciado via Leiningen)
- **Web**: Reitit (Roteamento) + Ring/Jetty (Servidor)
- **AWS SDK**: Java SDK V2 (software.amazon.awssdk)
- **Infra Local**: LocalStack (S3)

## 🛠️ Configuração do Ambiente

### 1. Iniciar o S3 Local (LocalStack)
Se estiver no **Bluefin (Podman)** ou **WSL2 (Docker)**, execute:

```bash
docker run --rm -it \
  -p 4566:4566 \
  -p 4510-4559:4510-4559 \
  -e SERVICES=s3 \
  localstack/localstack
```

### 2. Configurar Credenciais de Desenvolvimento
A SDK exige que existam credenciais configuradas, mesmo que "fakes" para o LocalStack:

```bash
aws configure set aws_access_key_id testing
aws configure set aws_secret_access_key testing
aws configure set region us-east-1
```

## 📦 Gerenciamento de Arquivos (CLI)

Use estes comandos para popular seu S3 local antes de testar a API.

> **Dica**: No WSL ou Bluefin, adicione `alias awslocal='aws --endpoint-url=http://127.0.0.1:4566'` ao seu `~/.zshrc`.

### Criar o Bucket
```bash
aws --endpoint-url=http://127.0.0.1:4566 s3 mb s3://meu-bucket-de-testes
```

### Preparar e Subir um arquivo de teste
```bash
# Criar um CSV e zipar
echo "id,nome\n1,Teste" > dados.csv
zip 123-s3.zip dados.csv

# Upload para o S3
aws --endpoint-url=http://127.0.0.1:4566 s3 cp 123-s3.zip s3://meu-bucket-de-testes/123-s3.zip
```

### Listar arquivos
```bash
aws --endpoint-url=http://127.0.0.1:4566 s3 ls s3://meu-bucket-de-testes --recursive
```

## 💻 Execução da Aplicação

### 1. Dependências (project.clj)
Certifique-se de que seu arquivo de projeto contenha:

```clojure
:dependencies [[org.clojure/clojure "1.11.1"]
               [metosin/reitit "0.7.0"]
               [ring/ring-core "1.11.0"]
               [ring/ring-jetty-adapter "1.11.0"]
               [software.amazon.awssdk/s3 "2.25.10"]
               [software.amazon.awssdk/auth "2.25.10"]]
```

### 2. Rodar o Servidor
```bash
lein run
```

## 🛰️ API Endpoints

| Método | Endpoint              | Parâmetro | Descrição                               |
|--------|-----------------------|-----------|-----------------------------------------|
| GET    | `/api/health`         | -         | Verifica se a API está online           |
| GET    | `/api/download?id=123`| `id`      | Faz o download do arquivo `{id}-s3.zip` |

**Exemplo de URL:** `http://localhost:3000/api/download?id=123`

## 🔍 Notas para Desenvolvedores

### 🐧 No Bluefin (Distrobox)
- Sempre use `127.0.0.1` em vez de `localhost` no código Clojure para evitar problemas de resolução IPv6 dentro do container.
- O Neovim (LazyVim) deve ser aberto dentro do `distrobox enter dev-web` para que o LSP encontre as dependências do Leiningen.

### 🪟 No WSL2
- O Windows compartilha o `localhost` com o WSL2. Você pode abrir o browser no Windows e acessar a API rodando no Linux normalmente.
- Se o Docker estiver no Windows (Docker Desktop), garanta que a integração WSL2 está ligada nas configurações do Docker.

## 🛡️ Tratamento de Erros

A aplicação utiliza um bloco `try/catch` no handler de download. Se o arquivo não existir no S3, a API retornará um `404 Not Found` com a mensagem "Arquivo não encontrado".