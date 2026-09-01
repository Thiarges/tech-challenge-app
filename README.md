# tech-challenge-app (Aplicação Principal no Kubernetes)

Sistema de gestão de oficina mecânica desenvolvido como parte do **Tech Challenge — Fase 3 da FIAP (Pós-Tech Software Architecture)**.

Este repositório contém a aplicação principal em **Spring Boot (Java 21)**, seus manifestos Kubernetes para deploy no **Amazon EKS**, sua pipeline de CI/CD automatizada via **GitHub Actions** e os mecanismos de segurança e observabilidade.

---

## 🏗️ Visão Geral da Arquitetura

O ecossistema completo é composto por **4 repositórios segregados**:

1. `fiap-fase3-infraestrutura`: Terraform provisionando VPC, subnets, ECR e cluster Amazon EKS.
2. `tech-challenge-infra-db`: Terraform provisionando o banco de dados gerenciado Amazon RDS PostgreSQL 16.
3. `tech-challenge-serverless`: AWS SAM provisionando a Lambda de autenticação por CPF e o Amazon API Gateway.
4. `tech-challenge-app` **(Este Repositório)**: Aplicação principal com as regras de negócio da oficina rodando em contêineres no EKS.

```
                                  ┌───────────────────────────────┐
                                  │      Amazon API Gateway       │
                                  └───────────────┬───────────────┘
                                                  │
                         ┌────────────────────────┴────────────────────────┐
                         │                                                 │
                   POST /auth                                        /{proxy+}
                         ▼                                                 ▼
        ┌───────────────────────────────────┐             ┌───────────────────────────────────┐
        │       AWS Lambda (Auth CPF)       │             │   AWS Network Load Balancer       │
        │   - Valida CPF do cliente         │             │   - Service Kubernetes (Porta 80) │
        │   - Gera token JWT assinado       │             └─────────────────┬─────────────────┘
        └─────────────────┬─────────────────┘                               │
                          │                                                 │
                          │                                                 ▼
                          │                              ┌─────────────────────────────────────┐
                          │                              │  Amazon EKS (Namespace: challenge) │
                          │                              │                                     │
                          │                              │   ┌─────────────────────────────┐   │
                          │                              │   │  Pods Spring Boot (2 a 10)  │   │
                          │                              │   │  - Validação JWT            │   │
                          │                              │   │  - Regras de negócio OS/Peça│   │
                          │                              │   │  - Actuator Probes/Métricas │   │
                          │                              │   │  - Migrations Flyway        │   │
                          │                              │   └──────────────┬──────────────┘   │
                          │                              │                  │                  │
                          │                              │       HPA (CPU 70% / Mem 80%)       │
                          │                              └──────────────────┼──────────────────┘
                          │                                                 │
                          ▼                                                 ▼
        ┌──────────────────────────────────────────────────────────────────────────────────────┐
        │                         Amazon RDS PostgreSQL 16 (Schema: oficina)                    │
        └──────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 🛠️ Tecnologias Utilizadas

- **Java 21** (Amazon Corretto / Eclipse Temurin)
- **Spring Boot 4.0.5** (Spring MVC, Spring Data JPA, Spring Security, Spring Actuator)
- **PostgreSQL 16 & Flyway** (13 migrations versionadas gerenciando o schema `oficina`)
- **JSON Web Tokens (JWT)** via `jjwt` (assinatura HMAC-SHA256 compartilhada)
- **SpringDoc OpenAPI 3** (Swagger UI interativo)
- **Docker** (Multi-stage build otimizado para `linux/amd64` com usuário não-root)
- **Kubernetes 1.36 / Amazon EKS** (Deployment, LoadBalancer Service, HPA, ConfigMap, Secret, PDB)
- **GitHub Actions** (CI com testes e JaCoCo + CD com deploy automatizado via `kubectl`)
- **Observabilidade:** Spring Actuator (`/actuator/health`, `/actuator/prometheus`) e logs estruturados em JSON via `logback-spring.xml`.

---

## 📂 Estrutura de Diretórios

```
tech-challenge-app/
├── .github/workflows/
│   ├── pr.yml                 # CI: Testes automatizados e verificação de cobertura (JaCoCo)
│   └── deploy.yml             # CD: Build JAR, Push ECR e Deploy no EKS
├── app/
│   ├── src/main/java/...      # Código-fonte da aplicação (Clean Architecture / DDD)
│   ├── src/main/resources/
│   │   ├── application.properties
│   │   ├── logback-spring.xml  # Logs em JSON para ambiente de nuvem
│   │   └── db/migration/       # 13 migrations Flyway
│   ├── src/test/java/...      # Testes unitários e de integração
│   ├── build.gradle           # Configurações Gradle, plugins JaCoCo e SonarQube
│   └── Dockerfile             # Multi-stage build otimizado para linux/amd64
├── k8s/
│   ├── configmap.yaml          # Configurações não-sensíveis da aplicação
│   ├── secret.yaml.example     # Template de segredos (para referência)
│   ├── deployment.yaml         # Deployment com Probes, Limits/Requests e Non-root user
│   ├── service.yaml            # Service LoadBalancer (AWS NLB)
│   ├── hpa.yaml                # Autoscaling horizontal (2 a 10 réplicas)
│   └── pod-disruption-budget.yaml
├── docker-compose.yml          # Execução local da stack completa (App + DB + Sonar)
├── settings.gradle
├── gradlew & gradlew.bat
└── README.md
```

---

## ⚙️ Variáveis de Ambiente e Configurações

| Variável | Descrição | Valor Padrão (Local) |
|---|---|---|
| `SPRING_DATASOURCE_URL` / `DB_URL` | URL de conexão JDBC com o PostgreSQL | `jdbc:postgresql://localhost:5432/oficina` |
| `SPRING_DATASOURCE_USERNAME` / `DB_USER` | Usuário do banco de dados | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` / `DB_PASSWORD` | Senha do banco de dados | `admin` |
| `JWT_SECRET` | Chave secreta compartilhada (mínimo 32 caracteres) | `minha-chave-secreta-dev-minimo-32-chars!!` |
| `JWT_ACCESS_EXP` | Tempo de expiração do token em segundos | `3600` (1 hora) |
| `WEBHOOK_APROVACAO_SECRET` | Chave para validação de webhooks de transição | `webhook-secret-key-123` |

---

## 🚀 Como Executar Localmente

### Pré-requisitos
- Docker e Docker Compose instalados
- Java 21 (opcional, caso queira rodar fora do Docker)

### Opção 1: Via Docker Compose (Recomendado)
Para subir o banco PostgreSQL e a aplicação compilada:
```bash
docker compose up --build -d
```

A aplicação estará disponível em `http://localhost:8080`.

### Opção 2: Via Gradle (Desenvolvimento)
Suba apenas o banco de dados:
```bash
docker compose up -d db
```

Execute a aplicação:
```bash
./gradlew bootRun
```

### Executar Testes Automatizados e Cobertura
```bash
./gradlew test jacocoTestReport jacocoTestCoverageVerification
```
O relatório HTML de cobertura é gerado em: `app/build/reports/jacoco/test/html/index.html`.

---

## ☸️ Deploy no Amazon EKS (Kubernetes)

O deploy é **100% automatizado** via GitHub Actions ao realizar push na branch `main`.

Caso deseje aplicar os manifestos manualmente via terminal:
```bash
# 1. Configurar o contexto do kubectl para o cluster EKS
aws eks update-kubeconfig --region us-east-1 --name tech-challenge

# 2. Criar os segredos no namespace tech-challenge
kubectl create secret generic app-secrets \
  --namespace=tech-challenge \
  --from-literal=SPRING_DATASOURCE_URL="jdbc:postgresql://<RDS_ENDPOINT>/tech_challenge" \
  --from-literal=SPRING_DATASOURCE_USERNAME="<DB_USER>" \
  --from-literal=SPRING_DATASOURCE_PASSWORD="<DB_PASSWORD>" \
  --from-literal=JWT_SECRET="<JWT_SECRET>" \
  --from-literal=WEBHOOK_APROVACAO_SECRET="<WEBHOOK_SECRET>" \
  --dry-run=client -o yaml | kubectl apply -f -

# 3. Aplicar os manifestos
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/hpa.yaml
kubectl apply -f k8s/pod-disruption-budget.yaml

# 4. Acompanhar o rollout
kubectl rollout status deployment/tech-challenge-app -n tech-challenge
```

---

## 🔄 CI/CD (GitHub Actions)

### Secrets Necessários no GitHub Repository:
Para o funcionamento correto da pipeline no GitHub Actions (`Settings` > `Secrets and variables` > `Actions`):

- `AWS_ACCESS_KEY_ID`: Chave de acesso AWS (Learner Lab).
- `AWS_SECRET_ACCESS_KEY`: Chave secreta AWS.
- `AWS_SESSION_TOKEN`: Token de sessão AWS (obrigatório para contas Learner Lab).
- `DB_URL`: JDBC URL do Amazon RDS (ex: `jdbc:postgresql://tech-challenge-db...us-east-1.rds.amazonaws.com:5432/tech_challenge`).
- `DB_USER`: Usuário do RDS.
- `DB_PASSWORD`: Senha do RDS.
- `JWT_SECRET`: Mesma chave utilizada na Lambda Serverless.
- `WEBHOOK_APROVACAO_SECRET`: Chave do webhook.

---

## 📖 Documentação da API (Swagger / OpenAPI)

Com a aplicação rodando (localmente ou na nuvem):

- **Swagger UI:** `http://<HOST>:8080/swagger-ui/index.html` (ou `/swagger-ui.html`)
- **OpenAPI JSON:** `http://<HOST>:8080/v3/api-docs`

---

## 🛡️ Qualidade de Código e Segurança

### SonarQube Local
Para rodar análise de código estático localmente:
```bash
docker compose --profile tools up -d sonarqube
./gradlew test jacocoTestReport sonar -Dsonar.token=<SEU_TOKEN_SONAR>
```

### OWASP ZAP (Análise Dinâmica de Segurança - DAST)
Para executar o scan de vulnerabilidades nas rotas da API:
```bash
# 1. Obter o token JWT de autenticação
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"login":"gerente","senha":"senha@123"}' | jq -r .accessToken)

# 2. Executar o scan do OWASP ZAP
mkdir -p reports
docker run --rm -v "$(pwd)/reports:/zap/wrk/:rw" -t zaproxy/zap-stable zap-api-scan.py \
  -t http://host.docker.internal:8080/v3/api-docs \
  -f openapi \
  -r zap-report.html \
  -z "-config replacer.full_list(0).description=auth -config replacer.full_list(0).enabled=true -config replacer.full_list(0).matchtype=REQ_HEADER -config replacer.full_list(0).matchstr=Authorization -config replacer.full_list(0).regex=false -config replacer.full_list(0).replacement=\"Bearer $TOKEN\""
```
O relatório é gerado em `reports/zap-report.html`.
