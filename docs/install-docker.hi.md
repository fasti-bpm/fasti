[English](install-docker.md) | [Tiếng Việt](install-docker.vi.md) | [Deutsch](install-docker.de.md) | [Español](install-docker.es.md) | [Français](install-docker.fr.md) | हिन्दी | [Bahasa Indonesia](install-docker.id.md) | [日本語](install-docker.ja.md) | [한국어](install-docker.ko.md) | [Português (Brasil)](install-docker.pt-BR.md) | [Русский](install-docker.ru.md) | [ไทย](install-docker.th.md) | [简体中文](install-docker.zh-CN.md)

> यह अनुवाद समुदाय द्वारा बनाए रखा जाता है और अंग्रेज़ी संस्करण से पीछे हो सकता है; अंग्रेज़ी संस्करण ही प्रामाणिक है। सुधारों का स्वागत है।

# Docker के साथ Fasti BPM इंस्टॉल करना

चरण-दर-चरण मार्गदर्शिका: H2 के साथ त्वरित परीक्षण, PostgreSQL के साथ प्रोडक्शन (`docker run` या
`docker compose`), मौजूदा Camunda 7 डेटाबेस से कनेक्ट करना, साथ-साथ संचालन, स्वास्थ्य
जाँच, अपग्रेड, रोलबैक और समस्या निवारण। इमेज का पूरा संदर्भ:
[distro/docker/README.md](../distro/docker/README.md)। Fasti BPM, Camunda 7 CE
7.24.0 पर आधारित एक स्वतंत्र प्रोजेक्ट है और Camunda Services GmbH से संबद्ध नहीं है।

<a id="1-requirements"></a>

## 1. आवश्यकताएँ

- Docker Compose v2 (`docker compose`) के साथ Docker Engine।
- वास्तविक कार्यभार के लिए: Fasti कंटेनर के लिए कम से कम 2 CPU और 2 GB मेमोरी (इससे कम होने पर
  JVM, Serial GC चुनता है)।
- प्रोडक्शन डेटाबेस: PostgreSQL, MariaDB, MySQL, SQL Server या Oracle (
  [परीक्षित संगतता तालिका](migrate-from-camunda-7.hi.md#limits) देखें)। DB2 सत्यापित नहीं है। H2 केवल मूल्यांकन के लिए है।

<a id="2-pull-the-image"></a>

## 2. इमेज पुल करें

<!-- update with the Docker Hub image name at release -->
इमेज amd64 और arm64 के लिए दो स्थानों पर प्रकाशित की जाती है:

```bash
docker pull fastidev/fasti-bpm:1.1.0              # Docker Hub
docker pull ghcr.io/fasti-bpm/fasti-bpm:1.1.0     # GitHub Container Registry
```

टैग: `1.1.0` (ठीक वही रिलीज़), `1.1` (1.1 की नवीनतम पैच रिलीज़) और `latest` (सबसे हाल में
प्रकाशित रिलीज़)। इमेज पर cosign (बिना कुंजी के, GitHub OIDC) से हस्ताक्षर किए गए हैं और इसमें
SBOM तथा उत्पत्ति का सत्यापन प्रमाण शामिल है। 1.0.1 से हस्ताक्षर cosign 3 के Sigstore bundle
प्रारूप में है; 1.0.0 इमेज पर cosign 2 के पुराने प्रारूप वाला हस्ताक्षर है।

आप स्रोत से भी इमेज बना सकते हैं (JDK 17+ और Maven
Central, nodejs.org तथा npm रजिस्ट्री तक नेटवर्क पहुँच आवश्यक है) और नीचे दिए गए प्रत्येक कमांड में
`fastidev/fasti-bpm:1.1.0` के स्थान पर `fasti-bpm:local` नाम का उपयोग कर सकते हैं:

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
```

<a id="3-quick-trial-with-h2"></a>

## 3. H2 के साथ त्वरित परीक्षण

```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e FASTI_ADMIN_USER=admin -e FASTI_ADMIN_PASSWORD='change-me' \
  -v fasti-data:/fasti/data \
  fastidev/fasti-bpm:1.1.0
```

- <http://localhost:8080/fasti/> खोलें और `admin` के रूप में साइन इन करें।
- `DB_URL` न होने पर इमेज `/fasti/data` में H2 का उपयोग करती है; कंटेनर दोबारा बनाए जाने पर
  `fasti-data` वॉल्यूम डेटा को बनाए रखता है। यह केवल मूल्यांकन के लिए है, वास्तविक डेटा के लिए कभी नहीं।
- उदाहरण प्रक्रिया (इनवॉइस) भी प्राप्त करने के लिए कमांड के अंत में `--webapps --rest --example`
  जोड़ें। केवल `--example` देने पर वेब UI और REST API बंद रहते हैं।
- REST API `http://localhost:8080/engine-rest` पर उपलब्ध है और इसके लिए HTTP Basic क्रेडेंशियल आवश्यक हैं;
  [REST API मार्गदर्शिका](api/README.md) देखें।

<a id="4-production-with-postgresql-docker-run"></a>

## 4. PostgreSQL के साथ प्रोडक्शन: `docker run`

दो सीक्रेट फ़ाइलें बनाएँ (डेटाबेस पासवर्ड और एडमिन पासवर्ड)। `--production` के साथ इंजन
अपनी पासवर्ड नीति लागू करता है: कम से कम 10 वर्ण, जिनमें बड़े और छोटे अक्षर, एक अंक
और एक विशेष वर्ण शामिल हों।

```bash
mkdir -p secrets
openssl rand -hex 24 > secrets/db-password.txt
echo "Fasti-1-$(openssl rand -hex 12)" > secrets/admin-password.txt
chmod 0700 secrets           # other users on the host cannot enter the directory
chmod 0644 secrets/*.txt     # the containers read them as uid 999 (PostgreSQL) and 1000 (Fasti)
```

```bash
docker network create fasti-net

docker run -d --name fasti-db --network fasti-net \
  -e POSTGRES_DB=fasti -e POSTGRES_USER=fasti \
  -e POSTGRES_PASSWORD_FILE=/run/secrets/db-password \
  -v "$PWD/secrets/db-password.txt:/run/secrets/db-password:ro" \
  -v fasti-pgdata:/var/lib/postgresql/data \
  postgres:16

docker run -d --name fasti --network fasti-net -p 127.0.0.1:8080:8080 \
  -e DB_DRIVER=org.postgresql.Driver \
  -e DB_URL=jdbc:postgresql://fasti-db:5432/fasti \
  -e DB_USERNAME=fasti -e DB_PASSWORD_FILE=/run/secrets/db-password \
  -v "$PWD/secrets/db-password.txt:/run/secrets/db-password:ro" \
  -e WAIT_FOR=fasti-db:5432 -e WAIT_FOR_TIMEOUT=60 \
  -e FASTI_DB_SCHEMA_UPDATE=true \
  -e FASTI_ADMIN_USER=admin -e FASTI_ADMIN_PASSWORD_FILE=/run/secrets/admin-password \
  -v "$PWD/secrets/admin-password.txt:/run/secrets/admin-password:ro" \
  -e TZ=UTC \
  --memory 2g --stop-timeout 60 \
  fastidev/fasti-bpm:1.1.0 --production
```

- `FASTI_DB_SCHEMA_UPDATE=true` केवल नए डेटाबेस में अनुपस्थित टेबल बनाता है; Fasti कभी
  मौजूदा स्कीमा को अपग्रेड नहीं करता। बाद में शुरू करते समय भी इसे सेट रखा जा सकता है।
- `--production`, `Secure` कुकी सक्षम करता है और स्वतः चुने गए H2 डेटाबेस तथा `DEBUG=true` को अस्वीकार करता है।
  Fasti के सामने रिवर्स प्रॉक्सी पर TLS टर्मिनेट करें; ब्राउज़र `Secure` कुकी केवल HTTPS पर (या
  `localhost` को) भेजते हैं।
- `WAIT_FOR`, डेटाबेस द्वारा कनेक्शन स्वीकार किए जाने तक स्टार्टअप को रोकता है।
- एडमिन पासवर्ड केवल तभी सेट किया जाता है जब एडमिन अभी मौजूद न हो; बाद में इसे Admin में बदलें।

<a id="5-production-with-postgresql-docker-compose"></a>

## 5. PostgreSQL के साथ प्रोडक्शन: `docker compose`

उदाहरण फ़ाइल: [distro/docker/examples/docker-compose.postgres.yml](../distro/docker/examples/docker-compose.postgres.yml)
(PostgreSQL 16, सीक्रेट, स्वास्थ्य जाँच, `--production`, 2 GB मेमोरी सीमा)।

```bash
cd distro/docker/examples          # or copy the file to a directory of its own
mkdir -p secrets
openssl rand -hex 24 > secrets/db-password.txt
echo "Fasti-1-$(openssl rand -hex 12)" > secrets/admin-password.txt
chmod 0700 secrets
chmod 0644 secrets/*.txt
docker compose -f docker-compose.postgres.yml up -d
docker compose -f docker-compose.postgres.yml ps      # both services "healthy"
```

शुरू करते समय सेट किए जा सकने वाले वेरिएबल: `FASTI_IMAGE` (डिफ़ॉल्ट `fastidev/fasti-bpm:1.1.0`, उदाहरण के
लिए `FASTI_IMAGE=fasti-bpm:local`), `FASTI_HOST_PORT` (डिफ़ॉल्ट `8080`), `TZ` (डिफ़ॉल्ट `UTC`)।

<a id="6-connecting-to-an-existing-camunda-7-database"></a>

## 6. मौजूदा Camunda 7 डेटाबेस से कनेक्ट करना

Fasti, 7.24 स्कीमा को बदले बिना उस पर चलता है। शुरू करने से पहले:

1. डेटाबेस का **बैकअप लें**।
2. स्कीमा संस्करण जाँचें: `select VERSION_ from ACT_GE_SCHEMA_LOG` — सबसे बड़ा मान
   `7.24.x` होना चाहिए। 7.17–7.23 स्कीमा के लिए: पहले `sql/upgrade` स्क्रिप्ट चलाएँ (इमेज में
   `/fasti/configuration/sql/upgrade` पर उपलब्ध हैं),
   [माइग्रेशन मार्गदर्शिका](migrate-from-camunda-7.hi.md#older-versions-717723) देखें।
3. प्राधिकरण जाँचें, क्योंकि Fasti उन्हें लागू करता है: `select * from ACT_ID_MEMBERSHIP where
   GROUP_ID_='camunda-admin'` और `select count(*) from ACT_RU_AUTHORIZATION`। यदि पुराने सिस्टम में
   प्राधिकरण बंद थे, तो ट्रैफ़िक स्थानांतरित करने से पहले सामान्य उपयोगकर्ताओं को अनुमतियाँ दें।
4. पुराने नोड की `TZ`, इतिहास स्तर, टेबल प्रीफ़िक्स/स्कीमा और पासवर्ड सेटिंग
   नोट कर लें।

```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e DB_DRIVER=org.postgresql.Driver \
  -e DB_URL=jdbc:postgresql://db.example.com:5432/camunda \
  -e DB_USERNAME=camunda -e DB_PASSWORD_FILE=/run/secrets/db-password \
  -v /path/to/db-password:/run/secrets/db-password:ro \
  -e WAIT_FOR=db.example.com:5432 \
  -e TZ=UTC \
  fastidev/fasti-bpm:1.1.0 --production
```

- पोर्ट केवल इस मशीन से बाइंड है; रिमोट पहुँच के लिए HTTPS वाले रिवर्स प्रॉक्सी का उपयोग करें।
- `FASTI_DB_SCHEMA_UPDATE` या `FASTI_ADMIN_*` सेट **न करें**: टेबल और खाते पहले से मौजूद हैं।
  स्टार्टअप लॉग में `ENGINE-03115 Database schema version 7.24.0 matches this engine.` होना चाहिए।
- इतिहास स्तर का डिफ़ॉल्ट `auto` है: डेटाबेस में संग्रहीत स्तर का उपयोग किया जाता है।
- अब प्रत्येक REST क्लाइंट को Basic क्रेडेंशियल या बेयरर टोकन भेजना होगा।
- ड्राइवर: PostgreSQL, MySQL, MariaDB, SQL Server और H2 शामिल हैं। **Oracle** और **DB2** ड्राइवर (`ojdbc11.jar`, `jcc.jar`) स्वयं `-v /driver/dir:/drivers:ro -e FASTI_EXTRA_LIB=/drivers` के साथ जोड़ें।
  SQL Server: ड्राइवर 13.x डिफ़ॉल्ट रूप से कनेक्शन एन्क्रिप्ट करता है और प्रमाणपत्र सत्यापित करता है;
  सर्वर को URL में दिए गए होस्ट नाम से मेल खाने वाला वैध प्रमाणपत्र चाहिए। `trustServerCertificate=true`
  (प्रमाणपत्र जाँच छोड़ता है) या `encrypt=false` (कोई एन्क्रिप्शन नहीं) केवल स्थानीय परीक्षण के लिए हैं। MySQL/MariaDB के लिए
  `transaction-isolation=READ-COMMITTED` आवश्यक है।

<a id="7-side-by-side-with-camunda-7-nodes"></a>

## 7. Camunda 7 नोड के साथ-साथ संचालन

Fasti और Camunda 7.24 एक ही डेटाबेस पर एक साथ चल सकते हैं (परीक्षित: दोनों एक-दूसरे का
डेटा देख सकते हैं)। शर्तें:

- समान इतिहास स्तर, समान `TZ`, समान पासवर्ड सेटिंग, समान टेबल प्रीफ़िक्स;
- यदि पुराने नोड ऐसे प्रक्रिया एप्लिकेशन हैं जिनमें Java क्लास (`JavaDelegate`, लिसनर) हैं जो Fasti में नहीं हैं, तो Fasti पर `-e FASTI_JOB_EXECUTOR_ENABLED=false` सेट करें या **प्रत्येक** नोड को
  डिप्लॉयमेंट-अवेयर बनाएँ (Fasti के लिए `FASTI_JOB_EXECUTOR_DEPLOYMENT_AWARE=true`)। यह केवल
  एसिंक्रोनस जॉब की सुरक्षा करता है: Fasti के माध्यम से इंस्टेंस शुरू करने या टास्क पूरा करने पर भी डेलिगेट
  Fasti पर चलते हैं, इसलिए ऐसे अनुरोध उन नोड को भेजें जिनमें Java क्लास मौजूद हैं;
- लोड बैलेंसर: वेब एप्लिकेशन के लिए स्टिकी सेशन; Basic क्रेडेंशियल वाले REST को इनकी आवश्यकता नहीं है।

विवरण: [Camunda 7 नोड के साथ मिश्रित संचालन](migrate-from-camunda-7.hi.md#mixed-operation-with-camunda-7-nodes)।

<a id="8-common-environment-variables"></a>

## 8. सामान्य एनवायरनमेंट वेरिएबल

| वेरिएबल | अर्थ |
|---|---|
| `DB_URL`, `DB_DRIVER`, `DB_USERNAME`, `DB_PASSWORD` (`_FILE`) | डेटाबेस कनेक्शन (पूर्व Camunda 7 Run इमेज के वेरिएबल नाम; वैकल्पिक नाम: `FASTI_DB_*`) |
| `WAIT_FOR`, `WAIT_FOR_TIMEOUT` | शुरू करने से पहले `host:port` की प्रतीक्षा करें |
| `TZ` | JVM समय क्षेत्र; प्रत्येक नोड पर समान होना चाहिए |
| `FASTI_DB_SCHEMA_UPDATE` | `true`: नए डेटाबेस में अनुपस्थित टेबल बनाएँ |
| `FASTI_ADMIN_USER`, `FASTI_ADMIN_PASSWORD` (`_FILE`) | एडमिन अनुपस्थित हो तो बनाएँ |
| `FASTI_JOB_EXECUTOR_ENABLED`, `FASTI_JOB_EXECUTOR_DEPLOYMENT_AWARE` | इस नोड का जॉब एक्ज़ीक्यूटर |
| `FASTI_EXTRA_LIB` | अतिरिक्त jar की डायरेक्टरी (Oracle, DB2 ड्राइवर) |
| `FASTI_DB_CONN_MAXACTIVE` | कनेक्शन पूल का अधिकतम आकार (डिफ़ॉल्ट 20; निष्क्रिय अवस्था में 10 खुले रहते हैं, `FASTI_DB_CONN_MINIDLE`) |
| `JAVA_OPTS`, `FASTI_MAX_RAM_PERCENTAGE` | JVM विकल्प, हीप के लिए मेमोरी का प्रतिशत (75) |

पूरी तालिका: [distro/docker/README.md](../distro/docker/README.md#environment-variables)।

<a id="9-health-checks"></a>

## 9. स्वास्थ्य जाँच

| एंडपॉइंट | उपयोग |
|---|---|
| `/actuator/health/liveness` | Kubernetes लिवनेस प्रोब |
| `/actuator/health/readiness` | रेडीनेस प्रोब, लोड बैलेंसर; डेटाबेस कनेक्शन और इंजन शामिल हैं |
| `/actuator/health` | समग्र स्वास्थ्य |

लॉगिन की आवश्यकता नहीं है; केवल स्वास्थ्य एंडपॉइंट उपलब्ध कराए जाते हैं। इमेज का `HEALTHCHECK`,
रेडीनेस का उपयोग करता है, इसलिए Fasti तैयार होते ही `docker ps` में `healthy` दिखाई देता है:

```bash
curl -s http://localhost:8080/actuator/health/readiness     # {"status":"UP"}
docker inspect -f '{{.State.Health.Status}}' fasti           # healthy
```

Kubernetes: `livenessProbe` → `/actuator/health/liveness`, `readinessProbe` →
`/actuator/health/readiness`, `terminationGracePeriodSeconds: 60`।

<a id="10-upgrade-and-rollback"></a>

## 10. अपग्रेड और रोलबैक

**Fasti को अपग्रेड करना** (उदाहरण के लिए `1.0.1` → `1.1.0`):

```bash
docker pull fastidev/fasti-bpm:1.1.0
docker stop -t 60 fasti && docker rm fasti
docker run -d --name fasti ... fastidev/fasti-bpm:1.1.0 --production   # same options as before
```

Docker Compose के साथ: `FASTI_IMAGE` (या `image:` पंक्ति) बदलें, फिर
`docker compose -f docker-compose.postgres.yml up -d` चलाएँ। पहले [परिवर्तन लॉग](../CHANGELOG.md)
पढ़ें, विशेष रूप से **असंगत बदलावों** की प्रविष्टियाँ। `1.x` श्रृंखला 7.24 स्कीमा बनाए रखती है, इसलिए इसके
भीतर अपग्रेड करने से स्कीमा नहीं बदलता। `docker stop -t 60`, कंटेनर बंद होने से पहले चल रहे
अनुरोधों को पूरा होने देता है।

**रोलबैक:**

- पिछली Fasti रिलीज़ पर: पुराने टैग के साथ दोबारा शुरू करें;
- Camunda 7.24 पर: Fasti बंद करें और उसी डेटाबेस पर Camunda 7.24 नोड दोबारा शुरू करें। नए UI की प्राथमिकताएँ बनी रहती हैं (`ACT_ID_INFO` में `fasti.prefs.*`, जिन्हें Camunda 7 अनदेखा करता है)। केवल Fasti में उपलब्ध सुविधाओं का उपयोग करने वाली डिप्लॉय की गई प्रक्रियाएँ Camunda 7 पर नहीं चलेंगी। देखें
  [Camunda 7.24 पर रोलबैक](migrate-from-camunda-7.hi.md#rollback-to-camunda-724)।

<a id="11-troubleshooting"></a>

## 11. समस्या निवारण

`docker logs fasti` (या `docker compose logs fasti`) से लॉग पढ़ें।

| लक्षण | कारण और समाधान |
|---|---|
| REST API, `401` लौटाता है | Fasti के लिए प्रमाणीकरण आवश्यक है: `-u user:password` या बेयरर टोकन भेजें |
| खाली सूचियाँ या `403` | उपयोगकर्ता के पास प्राधिकरण नहीं हैं: एडमिन → प्राधिकरण में संबंधित संसाधनों के लिए आवश्यक अनुमतियाँ दें। केवल वास्तविक प्रशासकों को `camunda-admin` में जोड़ें |
| `ENGINE-03111 The database schema has version …` के साथ बंद हो जाता है | स्कीमा 7.24 से पुराना है: बैकअप लें, फिर `sql/upgrade` स्क्रिप्ट चलाएँ (खंड 6)। `FASTI_SCHEMA_VERSION_CHECK=warn` से जाँच को बायपास न करें: पुराने स्कीमा पर चलाना समर्थित नहीं है और संचालन के दौरान विफल हो सकता है |
| `Insecure configuration refused` के साथ बंद हो जाता है | कोई सेटिंग REST प्रमाणीकरण या प्राधिकरण बंद करती है; उसे हटाएँ (केवल `FASTI_INSECURE_DEMO=true`, डेमो के लिए इंस्टेंस को खुली पहुँच देता है) |
| `Password does not match policy` के साथ बंद हो जाता है | `--production`, `FASTI_ADMIN_PASSWORD` पर पासवर्ड नीति लागू करता है: कम से कम 10 वर्ण, जिनमें बड़े और छोटे अक्षर, एक अंक और एक विशेष वर्ण शामिल हों |
| `password authentication failed` | सीक्रेट फ़ाइल का पासवर्ड डेटाबेस के पासवर्ड से अलग है। Windows पर बनाई गई फ़ाइलों के अंत में `\r\n` हो सकता है: उन्हें `tr -d '\r\n'` से दोबारा बनाएँ। PostgreSQL, `POSTGRES_PASSWORD_FILE` केवल तभी पढ़ता है जब वॉल्यूम पहली बार इनिशियलाइज़ किया जाता है |
| `FASTI_ADMIN_USER needs FASTI_ADMIN_PASSWORD` | `FASTI_ADMIN_PASSWORD` या `FASTI_ADMIN_PASSWORD_FILE` सेट करें |
| `--production needs a database` | कोई डेटाबेस कॉन्फ़िगर नहीं है; `--production`, डेमो H2 डेटाबेस का विकल्प के रूप में उपयोग नहीं करता: `DB_URL` को प्रोडक्शन डेटाबेस पर सेट करें |
| किसी `_FILE` वेरिएबल के लिए `… cannot be read` | फ़ाइल कंटेनर में माउंट नहीं है या uid 1000 उसे पढ़ नहीं सकता (फ़ाइल का मोड `0644` होना चाहिए; उसकी डायरेक्टरी `0700` रह सकती है) |
| HTTP पर वेब लॉगिन करने से सेशन बना नहीं रहता | `Secure` कुकी (`--production`) केवल HTTPS पर (या `localhost` को) भेजी जाती है: प्रॉक्सी पर TLS टर्मिनेट करें |
| SQL Server से कनेक्ट नहीं हो पा रहा (प्रमाणपत्र त्रुटि) | SQL Server को URL में दिए गए होस्ट नाम से मेल खाने वाला वैध प्रमाणपत्र दें; `trustServerCertificate=true` केवल स्थानीय परीक्षण के लिए है |
| कंटेनर बार-बार रीस्टार्ट होता है और लॉग में `OutOfMemoryError` दिखाई देता है | मेमोरी समाप्त होने पर JVM बंद हो जाता है: कंटेनर की मेमोरी सीमा बढ़ाएँ (2 GB) |

अभी भी समस्या है? लॉग के साथ GitHub इश्यू खोलें (पासवर्ड हटाकर)। सुरक्षा संबंधी समस्याओं के लिए:
[SECURITY.md](../SECURITY.md#security-policy) देखें।

<a id="12-without-docker"></a>

## 12. Docker के बिना

तैयार Tomcat 1.1.0 बंडल (डाउनलोड पृष्ठ: https://fasti.dev/download):
- [fasti-bpm-tomcat-1.1.0.zip](https://assets.fasti.dev/releases/1.1.0/fasti-bpm-tomcat-1.1.0.zip) ([SHA-256](https://assets.fasti.dev/releases/1.1.0/fasti-bpm-tomcat-1.1.0.zip.sha256))
- [fasti-bpm-tomcat-1.1.0.tar.gz](https://assets.fasti.dev/releases/1.1.0/fasti-bpm-tomcat-1.1.0.tar.gz) ([SHA-256](https://assets.fasti.dev/releases/1.1.0/fasti-bpm-tomcat-1.1.0.tar.gz.sha256))

एक्सट्रैक्ट करने से पहले चेकसम सत्यापित करें, उदाहरण के लिए `sha256sum -c fasti-bpm-tomcat-1.1.0.zip.sha256`।
