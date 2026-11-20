[English](README.md) | [Tiếng Việt](README.vi.md) | [Deutsch](README.de.md) | [Español](README.es.md) | [Français](README.fr.md) | हिन्दी | [Bahasa Indonesia](README.id.md) | [日本語](README.ja.md) | [한국어](README.ko.md) | [Português (Brasil)](README.pt-BR.md) | [Русский](README.ru.md) | [ไทย](README.th.md) | [简体中文](README.zh-CN.md)

> यह अनुवाद समुदाय द्वारा बनाए रखा जाता है और अंग्रेज़ी संस्करण से पीछे हो सकता है; अंग्रेज़ी संस्करण ही प्रामाणिक है। सुधारों का स्वागत है।

# Fasti BPM Platform

**Make Camunda 7 Great Again.** Camunda 7 Community Edition का समापन इसकी अंतिम
रिलीज़ 7.24.0 के साथ हुआ। Fasti BPM इसे वहीं से आगे बढ़ाता है: उसी डेटाबेस पर वही इंजन,
निरंतर सुधारों और लाइब्रेरी अपग्रेड के साथ, और एक नया वेब UI।

> **Fasti BPM, Camunda Services GmbH से संबद्ध नहीं है; Camunda, Camunda Services GmbH का ट्रेडमार्क है।**
> Fasti BPM एक स्वतंत्र ओपन-सोर्स प्रोजेक्ट (Apache-2.0) है। यह Camunda Services GmbH का हिस्सा नहीं है, न ही उसके द्वारा प्रायोजित
> या अनुमोदित है। "Camunda" नाम का उपयोग केवल यह बताने के लिए किया जाता है कि
> सोर्स कोड कहाँ से आया है और Fasti किसके साथ संगत है।

<a id="quick-start"></a>

## त्वरित शुरुआत

इमेज Docker Hub (`fastidev/fasti-bpm`) और GitHub
Container Registry (`ghcr.io/fasti-bpm/fasti-bpm`) पर amd64 और arm64 के लिए उपलब्ध है:

<!-- update with the Docker Hub image name at release -->
```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e FASTI_ADMIN_USER=admin -e FASTI_ADMIN_PASSWORD='change-me' \
  fastidev/fasti-bpm:1.0.0
```

<http://localhost:8080/fasti/> खोलें और साइन इन करें। `DB_URL` के बिना इमेज कंटेनर के भीतर H2 डेटाबेस
का उपयोग करती है, जो केवल मूल्यांकन के लिए है। REST API के लिए Basic क्रेडेंशियल आवश्यक हैं:

```bash
curl -i http://localhost:8080/engine-rest/task      # 401: no credentials
curl -u admin:'change-me' http://localhost:8080/engine-rest/task
```

सोर्स से **इमेज स्वयं बनाएँ**। इसके लिए Docker, JDK 17+ और Maven Central, nodejs.org तथा npm रजिस्ट्री तक नेटवर्क
पहुँच आवश्यक है (बिल्ड वेब UI के लिए Node/npm डाउनलोड करता
है); पहले बिल्ड में लगभग 10–20 मिनट लगते हैं:

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
# then run the docker run command above with fasti-bpm:local instead of fastidev/fasti-bpm:1.0.0
```

इमेज **डिफ़ॉल्ट रूप से REST प्रमाणीकरण अनिवार्य करती है और प्राधिकरण लागू करती है** (Camunda
Run से भिन्न); केवल `FASTI_INSECURE_DEMO=true` इन्हें डेमो के लिए बंद करता है।

<a id="highlights"></a>

## प्रमुख विशेषताएँ

- `/fasti/` पर **नया वेब UI** (React):
  - AND/OR से संयोजित कई वेरिएबल के आधार पर एक साथ प्रोसेस इंस्टेंस खोजें;
  - टास्क के भीतर केस की समयरेखा (हर चरण पर टिप्पणियाँ और कार्रवाइयाँ);
  - "अगला काम लें" बटन;
  - नियत तिथियाँ और तात्कालिकता (SLA), समय-सीमा पार कर चुका काम;
  - प्रत्येक उपयोगकर्ता और समूह के अनुसार टीम का कार्यभार;
  - इंसिडेंट से जुड़ी रनबुक;
  - सिस्टम की स्थिति का पृष्ठ (इंसिडेंट, विफल जॉब, विलंबित जॉब, एक्सटर्नल टास्क, बैच);
  - मोबाइल के अनुकूल लेआउट;
  - 13 भाषाएँ।
- **सिंगल साइन-ऑन (OIDC)**: Keycloak, Microsoft Entra ID और अन्य OpenID Connect प्रदाता;
  क्लेम को समूहों और टेनेंट से मैप किया जाता है; REST API के लिए JWT बियरर टोकन। Run,
  Spring Boot, Tomcat और WildFly के लिए उपलब्ध।
- **सुरक्षा सुदृढ़ीकरण**: क्वेरी और फ़िल्टर में एक्सप्रेशन के लिए अनुमति-सूची, इंजन के भीतर अटैचमेंट और
  टिप्पणी की जाँच, प्रारंभिक सेटअप, Cockpit अनुमतियाँ, ओपन रीडायरेक्ट, वेब
  एप्लिकेशन हेडर, कुकी और CSP। विवरण [परिवर्तन विवरण](CHANGELOG.md) में हैं।
- **डिफ़ॉल्ट रूप से प्रमाणीकरण चालू**: इमेज REST के लिए क्रेडेंशियल अनिवार्य करती है और
  हर मोड में प्राधिकरण लागू करती है।
- **स्कीमा संस्करण सुरक्षा-जाँच**: इंजन 7.24 से पुराने स्कीमा पर शुरू होने से इनकार करता है और कभी भी
  स्वयं स्कीमा अपग्रेड नहीं करता।
- **Java4Shell जोखिम न्यूनीकरण** ([CVE-2026-114938](docs/security/CVE-2026-114938.md)): amd64
  इमेज में सुधारा गया Temurin JRE शामिल है और यह Base64 intrinsic को बंद करती है; प्रभावित JVM पर
  चलने पर इंजन चेतावनी देता है।
- **प्रदर्शन**: नए वेब UI के पहले लोड का आकार 960 KB से घटकर 330 KB हो गया है (gzip डिफ़ॉल्ट रूप से
  चालू); ट्यूनिंग से जॉब एक्ज़ीक्यूटर का थ्रूपुट लगभग 2.5x बढ़ता है (आँकड़े और परीक्षण सेटअप
  [distro/docker/README.md](distro/docker/README.md#performance-tuning) में हैं)।
- **वर्तमान प्लेटफ़ॉर्म**: Java 17 या बाद का संस्करण, Spring Boot 4.1, GraalJS 25, Jackson 2.21।

<a id="installation"></a>

## स्थापना

<a id="one-command-on-your-existing-camunda-724-database"></a>

### आपके मौजूदा Camunda 7.24 डेटाबेस पर एक कमांड

इमेज पूर्व Camunda 7 Run इमेज के `DB_*` वेरिएबल स्वीकार करती है। डेटाबेस का बैकअप लें,
फिर:

<!-- update with the Docker Hub image name at release -->
```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e DB_DRIVER=org.postgresql.Driver \
  -e DB_URL=jdbc:postgresql://db.example.com:5432/camunda \
  -e DB_USERNAME=camunda -e DB_PASSWORD_FILE=/run/secrets/db-password \
  -v /path/to/db-password:/run/secrets/db-password:ro \
  -e TZ=Europe/Berlin \
  fastidev/fasti-bpm:1.0.0
```

उपयोगकर्ता, पासवर्ड, समूह, टेनेंट, प्राधिकरण, फ़िल्टर, डिप्लॉयमेंट, चल रहे इंस्टेंस,
इतिहास, जॉब और इंसिडेंट यथावत रहते हैं। पोर्ट केवल इसी मशीन से बाइंड होता है; रिमोट
पहुँच के लिए HTTPS वाले रिवर्स प्रॉक्सी का उपयोग करें। किसी मौजूदा खाते से `/fasti/` पर साइन इन करें।
प्रमाणीकरण चालू होने के कारण, प्रत्येक REST क्लाइंट (एक्सटर्नल टास्क वर्कर, इंटीग्रेशन) को
Basic क्रेडेंशियल या बियरर टोकन भेजना होगा।

- उसी डेटाबेस पर Camunda 7.24 CE नोड के **साथ-साथ**: समान इतिहास स्तर, समान
  `TZ`, समान पासवर्ड सेटिंग। यदि पुराने नोड ऐसे प्रोसेस एप्लिकेशन हैं जिनकी Java क्लास
  Fasti में नहीं हैं, तो Fasti पर `FASTI_JOB_EXECUTOR_ENABLED=false` सेट करें (या हर नोड को
  डिप्लॉयमेंट-अवेयर बनाएँ), और Fasti के माध्यम से उन प्रोसेस को शुरू या उनके टास्क पूरे न करें:
  सिंक्रोनस कॉल डेलीगेट को उसी नोड पर चलाती है जिसे कॉल प्राप्त होती है।
- **रोलबैक**: Fasti स्कीमा नहीं बदलता, इसलिए Fasti को रोकें और उसी डेटाबेस पर Camunda 7.24 नोड
  फिर से शुरू करें। केवल Fasti में उपलब्ध सुविधाओं का उपयोग करने वाले डिप्लॉय किए गए प्रोसेस
  Camunda 7 पर नहीं चलेंगे; [Camunda 7.24 पर रोलबैक](docs/migrate-from-camunda-7.hi.md#rollback-to-camunda-724) देखें।
- **पुराने स्कीमा (7.17–7.23)**: पहले इमेज में शामिल `sql/upgrade` स्क्रिप्ट
  (`/fasti/configuration/sql/upgrade`) चलाकर 7.24 तक अपग्रेड करें; उदाहरण के लिए 7.23 → 7.24:
  `postgres_engine_7.23_to_7.24.sql`।

इमेज के साथ परीक्षण किया गया (Camunda Run 7.24.0 द्वारा बनाए गए डेटाबेस को अपनाना, रोलबैक
करना, साथ-साथ चलाना): **PostgreSQL 16, MariaDB 11.8, MySQL 8.4, SQL Server 2022, Oracle
Database 23** (Oracle ड्राइवर शामिल नहीं है; `ojdbc11` स्वयं जोड़ें; MySQL पर रोलबैक का परीक्षण
नहीं किया गया)। **DB2
सत्यापित नहीं है।** H2 केवल मूल्यांकन के लिए है। प्रत्येक परीक्षण के परिणाम और सीमाएँ:
[माइग्रेशन गाइड](docs/migrate-from-camunda-7.hi.md#limits)।

विस्तृत गाइड:

- [Docker के साथ स्थापना](docs/install-docker.hi.md): H2, `docker run` और
  `docker compose` के साथ PostgreSQL, मौजूदा डेटाबेस, साथ-साथ चलाने का मोड, हेल्थ चेक, अपग्रेड, रोलबैक,
  समस्या निवारण।
- [Camunda 7 से माइग्रेशन](docs/migrate-from-camunda-7.hi.md): चरण, क्या समान है और क्या
  अलग है, रोलबैक, सीमाएँ।
- [इमेज के एनवायरनमेंट वेरिएबल का संदर्भ](distro/docker/README.md#environment-variables)।

<a id="without-docker-tomcat-bundle-download-from-fastidev"></a>

### Docker के बिना: Tomcat बंडल (fasti.dev से डाउनलोड करें)

तैयार Tomcat 1.0.0 बंडल (डाउनलोड पृष्ठ: https://fasti.dev/download):
- [fasti-bpm-tomcat-1.0.0.zip](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.zip) ([SHA-256](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.zip.sha256))
- [fasti-bpm-tomcat-1.0.0.tar.gz](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.tar.gz) ([SHA-256](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.tar.gz.sha256))

एक्सट्रैक्ट करने से पहले चेकसम सत्यापित करें, उदाहरण के लिए `sha256sum -c fasti-bpm-tomcat-1.0.0.zip.sha256`।

Tomcat वितरण को सोर्स (`distro/tomcat`) से भी बनाया जा सकता है। शुरू करने से पहले बंडल में
`README.txt` पढ़ें: REST API (`/engine-rest`) में डिफ़ॉल्ट रूप से HTTP Basic प्रमाणीकरण आवश्यक है,
कोई उपयोगकर्ता नहीं बनाया जाता (`/fasti/` के सेटअप पृष्ठ पर पहला व्यवस्थापक बनाएँ),
और Camunda Run डेटाबेस अपनाते समय `conf/bpm-platform.xml` में
`jobExecutorDeploymentAware=false` सेट करें, अन्यथा मौजूदा डिप्लॉयमेंट के टाइमर और एसिंक्रोनस जॉब नहीं चलेंगे।

<a id="a-ui-built-from-real-user-complaints"></a>

## वास्तविक उपयोगकर्ता शिकायतों के आधार पर बना UI

नए वेब UI की सुविधाएँ Camunda 7 उपयोगकर्ताओं द्वारा Camunda फ़ोरम और GitHub पर सार्वजनिक रूप से लिखी गई
बातों के आधार पर चुनी गईं, उदाहरण के लिए:

| उपयोगकर्ताओं ने क्या माँगा | स्रोत | Fasti में |
|---|---|---|
| OR के साथ कई वेरिएबल के आधार पर इंस्टेंस खोजें | फ़ोरम [39269](https://forum.camunda.io/t/39269), [45695](https://forum.camunda.io/t/45695), [48976](https://forum.camunda.io/t/48976), [64208](https://forum.camunda.io/t/64208), [28855](https://forum.camunda.io/t/28855), [4678](https://forum.camunda.io/t/4678); [camunda-bpm-platform#3236](https://github.com/camunda/camunda-bpm-platform/issues/3236) | कई वेरिएबल के लिए शर्तें बनाने का उपकरण, AND/OR |
| केस के हर चरण की टिप्पणियाँ देखें | फ़ोरम [30260](https://forum.camunda.io/t/30260), [52109](https://forum.camunda.io/t/52109), [2173](https://forum.camunda.io/t/2173) | टास्क में केस की समयरेखा; सीधे प्रोसेस इंस्टेंस पर टिप्पणियाँ |
| वेरिएबल के आधार पर टास्क क्रमबद्ध करें, सबसे पुराने पहले | फ़ोरम [17599](https://forum.camunda.io/t/17599), [42078](https://forum.camunda.io/t/42078), [41193](https://forum.camunda.io/t/41193), [67328](https://forum.camunda.io/t/67328) | वेरिएबल, नियत तिथियों और तात्कालिकता के आधार पर क्रमबद्ध करना |
| प्रत्यायोजन, अनुपस्थिति के दौरान स्थानापन्न | फ़ोरम [2788](https://forum.camunda.io/t/2788), [38199](https://forum.camunda.io/t/38199), [9331](https://forum.camunda.io/t/9331) | प्रत्येक टास्क का प्रत्यायोजन उपलब्ध है; कार्यालय से अनुपस्थिति के दौरान स्थानापन्न [रोडमैप](#roadmap-plan) में हैं |
| फ़ॉर्म के बिना प्रोसेस शुरू करते समय बिज़नेस की और वेरिएबल | [cibseven-webclient#528](https://github.com/cibseven/cibseven-webclient/issues/528) | हाँ |
| डिप्लॉय किया गया BPMN XML डाउनलोड करें | [camunda/camunda#22005](https://github.com/camunda/camunda/issues/22005) | हाँ |
| एकरूप लेबल, कई भाषाएँ | [operaton#643](https://github.com/operaton/operaton/issues/643) | 13 भाषाएँ |

<a id="comparison"></a>

## तुलना

प्रत्येक प्रोजेक्ट के सार्वजनिक दस्तावेज़ों पर आधारित एक संक्षिप्त तुलना (नवंबर 2026 में पढ़े गए);
"सत्यापित नहीं" का अर्थ है कि हमें कोई विश्वसनीय स्रोत नहीं मिला। प्रत्येक प्रोजेक्ट की अपनी दिशा है और वह
अलग-अलग ज़रूरतों के लिए उचित विकल्प है; निर्णय लेने से पहले उनके दस्तावेज़ देखें।

| | लाइसेंस | मौजूदा C7 DB पर चलता है | माइग्रेशन आवश्यक | सुरक्षा पैच | Java / Spring Boot | UI | SSO | Docker इमेज |
|---|---|---|---|---|---|---|---|---|
| **Fasti BPM** | Apache-2.0 | हाँ, 7.24 स्कीमा अपरिवर्तित | इमेज: कोई नहीं। एम्बेडेड ऐप: नए Maven निर्देशांक, Spring Boot 4.1 और Java 17+; `org.camunda.*` पैकेज बरकरार | हाँ, [नीचे दी गई नीति](#commitments-support-and-security-patches) देखें | 17+ / 4.1 | नया (React) + क्लासिक (अप्रचलित घोषित) | OIDC अंतर्निहित | हाँ, 1.0.0 से |
| Camunda 7 CE 7.24 | Apache-2.0 सोर्स; कुछ शामिल वेब घटक MIT के अंतर्गत ([13]) | (मूल) | — | 7.24 के बाद कोई CE रिलीज़ नहीं ([1]) | 17, 21, 25 ([2]) / 3.5 | Cockpit, Tasklist, Admin (AngularJS) | सुरक्षा स्टार्टर के माध्यम से OAuth2 | पूर्व Run इमेज |
| Camunda 7 Enterprise | व्यावसायिक | हाँ (वही उत्पाद शृंखला) | नहीं | 04/2030 तक, सशुल्क विस्तार 04/2032 तक ([3]) | सत्यापित नहीं | Cockpit Enterprise | सत्यापित नहीं | सत्यापित नहीं |
| Camunda 8 | Camunda License 1.0; 8.6 से स्वयं प्रबंधित प्रोडक्शन के लिए लाइसेंस आवश्यक ([4]) | नहीं (अलग इंजन) | हाँ (मॉडल, इंस्टेंस, डेटा) | हाँ (विक्रेता) | अलग इंजन, तुलना योग्य नहीं | Operate, Tasklist | सत्यापित नहीं | सत्यापित नहीं |
| Operaton | Apache-2.0 | 7.24 जैसी ही टेबल संरचना; 2.2 एक अपग्रेड स्क्रिप्ट के माध्यम से स्कीमा संस्करण 7.25 दर्ज करता है ([5]) | पैकेज के नाम बदलकर `org.operaton.*` किए गए (OpenRewrite रेसिपी उपलब्ध); स्कीमा-संस्करण स्क्रिप्ट (2.2) | हाँ, 2.2.x शृंखला ([6]) | 17+ / 4.1 ([5]) | क्लासिक + webapps-neo (पूर्वावलोकन) | सत्यापित नहीं | हाँ ([7]) |
| CIB seven | Apache-2.0; EE के लिए लाइसेंस कुंजी आवश्यक है | हाँ (7.24 के बदलाव मर्ज किए गए) | पैकेज के नाम बदलकर `org.cibseven.*` किए गए, प्रीफ़िक्स `cibseven.*` | हाँ, प्रत्येक रिलीज़ में ([8]) | 17, 21 / 3.5 और 4.0 ([9]) | Vue 3 वेब क्लाइंट | REST के लिए OAuth2/JWT ([8]) | हाँ ([8]) |
| OrqueIO | Apache-2.0 | उनके दस्तावेज़ों के अनुसार C7 टेबल के साथ संगत ([10]) | Maven समूह `io.orqueio`; पैकेज का नाम बदलने का उल्लेख नहीं | हाँ, उनके दस्तावेज़ों के अनुसार ([10]) | 21 / 3.5, 4.0, 4.1 | Angular में पुनर्लेखन | कई प्रदाताओं वाला OIDC | सत्यापित नहीं |
| EximeeBPMS | Apache-2.0 (Monitor: MIT) | प्रोजेक्ट के अनुसार उसका स्कीमा Camunda 7 के साथ संगत रहता है (Fasti द्वारा सत्यापित नहीं) ([14]) | पैकेज के नाम बदलकर `org.eximeebpms.*` किए गए, Model API का नाम बदला गया | हाँ, दो नवीनतम माइनर रिलीज़ ([11]) | 21 / Spring Boot संस्करण का उल्लेख नहीं | अलग Monitor | सत्यापित नहीं | हाँ (`eximeebpms-docker` रिपॉज़िटरी) |
| Fluxnova | Apache-2.0 (FINOS) | सत्यापित नहीं | सत्यापित नहीं | सत्यापित नहीं | 21 (Corretto इमेज) / सत्यापित नहीं | Control Center | सत्यापित नहीं | हाँ ([12]) |

Fasti की अपनी कमियाँ, स्पष्ट रूप से: छोटी टीम वाला नया प्रोजेक्ट, अभी कोई व्यावसायिक
सहायता नहीं, DB2 सत्यापित नहीं।

स्रोत: [1] [CE जीवनचक्र समाप्ति की घोषणा](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921);
[2] [7.24 के समर्थित परिवेश](https://docs.camunda.org/manual/7.24/introduction/supported-environments/);
[3] [Enterprise सहायता का विस्तार](https://camunda.com/blog/2025/02/camunda-7-enterprise-end-of-life-extension/);
[4] [Camunda 8.6 लाइसेंसिंग](https://camunda.com/blog/2024/10/camunda-licensing-what-you-need-to-know/);
[5] [Operaton 2.2](https://docs.operaton.org/docs/documentation/reference/release-notes/2_2/);
[6] [Operaton SECURITY.md](https://github.com/operaton/operaton/blob/main/SECURITY.md);
[7] [operaton-docker](https://github.com/operaton/operaton-docker);
[8] [CIB seven 2.2.0](https://docs.cibseven.org/manual/latest/release-notes/release-notes-2.2.0-ce/);
[9] [CIB seven के समर्थित परिवेश](https://docs.cibseven.org/manual/latest/introduction/supported-environments/);
[10] [OrqueIO दस्तावेज़](https://docs.orqueio.io/);
[11] [EximeeBPMS SECURITY.md](https://github.com/EximeeBPMS/eximeebpms/blob/main/SECURITY.md);
[12] [Fluxnova](https://github.com/finos/fluxnova-bpm-platform);
[13] [Camunda 7 लाइसेंस](https://docs.camunda.org/manual/latest/introduction/licenses/);
[14] [EximeeBPMS](https://github.com/EximeeBPMS/eximeebpms);
स्वतंत्र अवलोकन: [WZ-IT, 2026-10-15](https://wz-it.com/en/blog/camunda-7-end-of-life-operaton-cibseven-fluxnova-compared/)।

<a id="timeline"></a>

## समयरेखा

<a id="camunda-7-milestones"></a>

### Camunda 7 के प्रमुख पड़ाव

| तिथि | प्रमुख पड़ाव | स्रोत |
|---|---|---|
| 2024-03-06 | Camunda ने अक्टूबर 2025 में Camunda 7 CE का जीवनचक्र समाप्त करने की घोषणा की, जिसमें 7.24 अंतिम CE रिलीज़ होगी | [Camunda फ़ोरम](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921) |
| 2025-02-28 | Camunda ने Camunda 7 Enterprise की सहायता 2030-04-09 तक बढ़ाई, और सशुल्क विस्तारित सहायता अप्रैल 2032 तक | [Camunda ब्लॉग](https://camunda.com/blog/2025/02/camunda-7-enterprise-end-of-life-extension/) |
| 2025-10-07 | `7.24.0` टैग बनाया गया और Maven Central पर प्रकाशित किया गया: अंतिम CE रिलीज़ | [टैग 7.24.0](https://github.com/camunda/camunda-bpm-platform/tree/7.24.0), [Maven Central](https://repo1.maven.org/maven2/org/camunda/bpm/camunda-engine/7.24.0/) |
| 2025-10-14 | Camunda द्वारा निर्धारित 7.24 की रिलीज़ तिथि; इस तिथि के बाद सुरक्षा पैच सहित कोई और CE रिलीज़ नहीं है। तब से CE रिपॉज़िटरी आर्काइव की जा चुकी है | [घोषणा](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921), [सहायता पृष्ठ](https://docs.camunda.org/enterprise/announcement) |
| अप्रैल 2030 | Camunda 7 Enterprise सहायता की समाप्ति (7.24 LTS: 2030-04-13) | [सहायता पृष्ठ](https://docs.camunda.org/enterprise/announcement) |
| अप्रैल 2032 | Camunda 7 Enterprise की सशुल्क विस्तारित सहायता की समाप्ति | [सहायता पृष्ठ](https://docs.camunda.org/enterprise/announcement) |

<a id="fasti-bpm"></a>

### Fasti BPM

Fasti सीधे `7.24.0` टैग से आगे बढ़ता है; हर सुधार और बदलाव की गणना उसी बिंदु से की जाती है
(`git log 7.24.0..`)। नीचे दिए गए चरण किए गए काम के क्रम में हैं, बिना तिथियों के:

1. **आधार**: Java 17, Spring Boot 4.1, लाइब्रेरी अपग्रेड; Maven निर्देशांक
   `dev.fasti.platform`।
2. **सुरक्षा**: 7.x शृंखला की सार्वजनिक रूप से उजागर समस्याओं के सुधार, एक्सप्रेशन अनुमति-सूची,
   इंजन के भीतर अटैचमेंट/टिप्पणी की जाँच, वेब एप्लिकेशन का सुदृढ़ीकरण।
3. `/fasti/` पर **नया वेब UI**, जो धीरे-धीरे क्लासिक Cockpit, Tasklist और Admin की जगह ले रहा है।
4. Run, Spring Boot, Tomcat और WildFly के लिए **सिंगल साइन-ऑन** (OIDC)।
5. **मौजूदा डेटाबेस पर सीधे उपयोग**: इमेज पुराने `DB_*` वेरिएबल स्वीकार करती है और स्कीमा संस्करण जाँचती है; पाँच डेटाबेस सिस्टम पर परीक्षण किया गया।
6. **रिलीज़-पूर्व सुरक्षा समीक्षा** (कोड समीक्षा, इमेज का पेनिट्रेशन परीक्षण, ZAP बेसलाइन
   स्कैन; मिली समस्याएँ ठीक की गईं) और **Java4Shell जोखिम न्यूनीकरण**।
7. **1.0.0**: पहली रिलीज़ (2026-11-20)।

<a id="roadmap-plan"></a>

## रोडमैप (योजना)

> यह एक **योजना** है, तिथियों की प्रतिबद्धता नहीं। दायरा और क्रम बदल सकते हैं।

### नवंबर और दिसंबर 2026 (प्रगति पर)

सबसे पहले, वह सब जो उपयोगकर्ता पहले ही माँग चुके हैं।

**1.x: चलाने में अधिक आसान**

- Maven Central पर Maven आर्टिफ़ैक्ट।
- Helm चार्ट और Kubernetes के नमूना मैनिफ़ेस्ट।
- Prometheus मेट्रिक्स और OpenTelemetry ट्रेसिंग।
- संरचित JSON लॉग।
- MyBatis अपग्रेड, जिससे OGNL भी अपडेट होता है।
- DB2 सत्यापन और मूल भाषियों द्वारा अनुवादों की समीक्षा।

**2.0: विनियमित क्षेत्रों में काम करने वाली टीमें क्या माँगती हैं**

- अनुपस्थिति के दौरान स्थानापन्न, ताकि छुट्टी पर गए व्यक्ति के कारण काम रुका न रहे
  ([फ़ोरम 2788](https://forum.camunda.io/t/2788), [38199](https://forum.camunda.io/t/38199))।
- अनुमोदनों के लिए दो व्यक्तियों द्वारा जाँच (फ़ोर-आइज़ नियंत्रण)।
- अपरिवर्तनीय ऑडिट ट्रेल, हस्ताक्षरित या हैश किया हुआ, ऑडिट रिपोर्ट के साथ।
- नए इंटरफ़ेस में CMMN और AngularJS ऐप की सेवानिवृत्ति।

**नए क्षेत्र**

- दीर्घकालिक सहायता शृंखलाएँ।
- इंजन के लिए एक MCP सर्वर।
- प्रोसेस में AI चरण।

### 2027 के लक्ष्य

- **2027 की पहली तिमाही: बड़े पैमाने पर संचालन**: रोलिंग अपग्रेड और स्केलिंग के लिए Kubernetes
  ऑपरेटर; इतिहास को Kafka और OpenSearch में स्ट्रीम करना, ताकि रनटाइम डेटाबेस छोटा रहे; प्रकाशित
  प्रदर्शन बेंचमार्क, जो हर रिलीज़ के लिए दोहराए जाएँगे।
- **2027 की दूसरी तिमाही: निर्माण करने वालों के लिए टूलकिट**: कनेक्टर SDK, साथ में REST, Kafka, ईमेल
  और ऑब्जेक्ट स्टोरेज के लिए कनेक्टर; परिदृश्य परीक्षणों और कवरेज रिपोर्ट वाली प्रोसेस टेस्ट किट;
  पुन: प्रयोज्य घटकों वाला फ़ॉर्म बिल्डर।
- **2027 की तीसरी तिमाही: देखें कि प्रोसेस कैसा प्रदर्शन करते हैं**: प्रोसेस एनालिटिक्स (हीटमैप,
  अड़चनें और अवधि रिपोर्ट); अलर्ट के साथ सेवा-स्तर लक्ष्य; एक सहायक जो टेक्स्ट से BPMN का मसौदा
  बनाता है और इंसिडेंट की व्याख्या करता है।
- **2027 की चौथी तिमाही: एंटरप्राइज़ स्तर पर भरोसा**: प्रत्येक टेनेंट के लिए कोटा सहित मल्टी-टेनेंट
  प्रशासन; स्वतंत्र सुरक्षा ऑडिट, जिसकी रिपोर्ट प्रकाशित की जाएगी; हर रिलीज़ के लिए हस्ताक्षरित
  प्रोवेनेंस के साथ पुनरुत्पादनीय बिल्ड।

<a id="commitments-support-and-security-patches"></a>

## प्रतिबद्धताएँ: सहायता और सुरक्षा पैच

> इस नीति का रखरखाव Fasti BPM प्रोजेक्ट टीम करती है और इसे अपडेट किया जा सकता है; इस
> रिपॉज़िटरी में मौजूद संस्करण ही प्रभावी है।

- **समर्थित शृंखला:** `1.x` शृंखला की नवीनतम माइनर रिलीज़ को बग सुधार और सुरक्षा
  पैच मिलते हैं (पैच रिलीज़ के रूप में, उदाहरण के लिए `1.0.1`)।
- **सुरक्षा पैच:** Fasti और उसमें शामिल लाइब्रेरी की सुरक्षा कमज़ोरियों को
  अन्य काम से अधिक प्राथमिकता दी जाती है; बेस इमेज या JRE में सुधार आने पर इमेज फिर से बनाई जाती है। प्रोजेक्ट
  Camunda 7 और उसके फ़ोर्क की सार्वजनिक सुरक्षा सलाहों पर नज़र रखता है और जाँचता है कि Fasti प्रभावित है या नहीं।
- **संगतता:** `1.x` शृंखला 7.24 डेटाबेस स्कीमा और `org.camunda.bpm.*`
  पैकेज बनाए रखती है। संगतता तोड़ने वाले बदलाव [परिवर्तन विवरण](CHANGELOG.md) में **BREAKING** के रूप में चिह्नित किए जाते हैं।
- **सहायता का स्तर:** यह सामुदायिक प्रोजेक्ट है; सहायता यथासंभव दी जाती है, **बिना किसी SLA के**,
  और अभी कोई व्यावसायिक सहायता उपलब्ध नहीं है।
- **सुरक्षा कमज़ोरियों की रिपोर्टिंग:** कृपया सार्वजनिक इश्यू **न खोलें**;
  [SECURITY.md](SECURITY.md#security-policy) देखें (GitHub के माध्यम से निजी रिपोर्टिंग)।
- **ठीक किए गए दोष:** [परिवर्तन विवरण](CHANGELOG.md) के *Fixed* और *Security* अनुभागों में
  Camunda 7 CE 7.24.0 की तुलना में किए गए सुधार सूचीबद्ध हैं; सुरक्षा सलाह
  [SECURITY.md](SECURITY.md#advisories) में सूचीबद्ध हैं।

<a id="documentation"></a>

## दस्तावेज़

| दस्तावेज़ | विषय-वस्तु |
|---|---|
| [docs/install-docker.md](docs/install-docker.hi.md) | Docker के साथ चरण-दर-चरण स्थापना |
| [docs/api/README.md](docs/api/README.md) | REST API इंटीग्रेशन, Java और JavaScript एक्सटर्नल टास्क क्लाइंट |
| [docs/migrate-from-camunda-7.md](docs/migrate-from-camunda-7.hi.md) | Camunda 7 से माइग्रेशन, रोलबैक, सीमाएँ |
| [distro/docker/README.md](distro/docker/README.md) | इमेज: एनवायरनमेंट वेरिएबल, ड्राइवर, संचालन, ट्यूनिंग |
| [webapps/frontend-next/README.md](webapps/frontend-next/README.md#new-web-ui-frontend-next) | नया वेब UI |
| [docs/security/CVE-2026-114938.md](docs/security/CVE-2026-114938.md#cve-2026-114938-java4shell-and-fasti-bpm) | Java4Shell और Fasti |
| [CHANGELOG.md](CHANGELOG.md), [SECURITY.md](SECURITY.md#security-policy), [CONTRIBUTING.md](CONTRIBUTING.md#how-to-contribute) | बदलाव, सुरक्षा, योगदान |

<a id="what-this-is"></a>

## यह क्या है

इस रिपॉज़िटरी में BPMN 2.0 / DMN प्रोसेस इंजन, REST API, वेब
एप्लिकेशन (Cockpit, Tasklist, Admin) और वितरण (Run, Tomcat, WildFly) शामिल हैं। यह
Camunda 7 CE के `7.24.0` टैग से शुरू होती है और बग सुधारों तथा लाइब्रेरी
अपग्रेड के साथ आगे बढ़ती है।

लक्ष्य Camunda 7 पर चलने वाले एप्लिकेशन के साथ **पश्च-संगतता** बनाए रखना है:

- `org.camunda.bpm.*` पैकेज, BPMN नेमस्पेस
  `http://camunda.org/schema/1.0/bpmn` और `camunda.bpm.*` कॉन्फ़िगरेशन प्रीफ़िक्स बरकरार हैं;
- 7.24 का डेटाबेस स्कीमा बरकरार है;
- सभी डिप्लॉयमेंट प्रकार बरकरार हैं: Spring Boot, Run, Tomcat, WildFly; `javax` और `jakarta`।

<a id="what-is-different-from-camunda-7240-ce"></a>

## Camunda 7.24.0 CE से क्या अलग है

- **सुरक्षा:** 7.x शृंखला की सार्वजनिक रूप से उजागर समस्याओं के सुधार (क्वेरी में
  एक्सप्रेशन, प्रारंभिक सेटअप, अटैचमेंट और टिप्पणियों तक पहुँच, Cockpit अनुमतियाँ, ओपन
  रीडायरेक्ट) तथा अतिरिक्त सुरक्षा उपाय: क्वेरी और
  फ़िल्टर में एक्सप्रेशन के लिए अनुमति-सूची, इंजन के भीतर अटैचमेंट और टिप्पणी की जाँच, सुदृढ़ सुरक्षा फ़िल्टर,
  वेब एप्लिकेशन में हेडर और कुकी।
- **स्थिरता:** JavaScript (GraalJS) से स्क्रिप्ट और DMN एक्सप्रेशन का मूल्यांकन करते समय अब मेमोरी लीक
  नहीं होता, और इंजन में कई अन्य सुधार भी किए गए हैं।
- **प्लेटफ़ॉर्म:** Java 17 या बाद का संस्करण, Spring Boot 4.1, GraalJS 25, Jackson 2.21 और कई अन्य
  लाइब्रेरी को सुरक्षा सुधारों वाली रिलीज़ तक अपग्रेड किया गया है।

कुछ सुधार अधिक सुरक्षित व्यवहार के लिए डिफ़ॉल्ट बदलते हैं; अपग्रेड करने से पहले
[माइग्रेशन गाइड](docs/migrate-from-camunda-7.hi.md#what-differs) में "क्या अलग है" पढ़ें।

<a id="versioning"></a>

## संस्करण निर्धारण

Fasti BPM Platform, `1.0.0` से शुरू करते हुए [सिमैंटिक वर्ज़निंग](https://semver.org/) का पालन करता है।
`1.x` शृंखला Camunda 7.24 के लिए लिखे गए एप्लिकेशन के साथ संगत है।

<a id="maven-coordinates"></a>

## Maven निर्देशांक

Fasti BPM Platform के आर्टिफ़ैक्ट के अपने Maven निर्देशांक हैं। **Java पैकेज
`org.camunda.bpm.*` ही रहते हैं**, इसलिए एप्लिकेशन कोड के इम्पोर्ट बरकरार रहते हैं; `camunda.bpm.*`
कॉन्फ़िगरेशन प्रीफ़िक्स, BPMN/DMN नेमस्पेस, डेटाबेस स्कीमा और
WildFly पर मॉड्यूल के नाम भी अपरिवर्तित हैं। Maven के संदर्भ में केवल डिपेंडेंसी घोषणाएँ
बदलती हैं (वेब एप्लिकेशन पथ अलग से बदलता है,
[वेब एप्लिकेशन पथ](#web-application-path-fasti) देखें):

- **groupId:** `org.camunda.bpm` → `dev.fasti.platform`, `org.camunda.bpm.<x>` →
  `dev.fasti.platform.<x>`; समूह `org.camunda.commons`, `org.camunda.connect`,
  `org.camunda.spin` और `org.camunda.template-engines` → `dev.fasti.platform.<group name>`।
- **artifactId:** प्रीफ़िक्स `camunda-` बदलकर `fasti-` हो जाता है, शेष बरकरार रहता है।
- **version:** `7.24.0` के बजाय Fasti BPM Platform का संस्करण
  (देखें [संस्करण निर्धारण](#versioning))।

इन आर्टिफ़ैक्ट की कोई रिलीज़ अभी Maven Central पर प्रकाशित नहीं हुई है। तब तक सोर्स
से बिल्ड करें और आर्टिफ़ैक्ट को अपने प्रोजेक्ट द्वारा उपयोग की जाने वाली Maven रिपॉज़िटरी में उपलब्ध कराएँ।

BOM से संस्करण प्रबंधित करें:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>dev.fasti.platform</groupId>
      <artifactId>fasti-bom</artifactId>
      <version>${fasti.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

अक्सर उपयोग किए जाने वाले आर्टिफ़ैक्ट:

| Camunda 7 | Fasti BPM Platform |
|---|---|
| `org.camunda.bpm:camunda-bom` | `dev.fasti.platform:fasti-bom` |
| `org.camunda.bpm:camunda-engine` | `dev.fasti.platform:fasti-engine` |
| `org.camunda.bpm:camunda-engine-spring` | `dev.fasti.platform:fasti-engine-spring` |
| `org.camunda.bpm:camunda-engine-spring-6` | `dev.fasti.platform:fasti-engine-spring-6` |
| `org.camunda.bpm:camunda-engine-cdi-jakarta` | `dev.fasti.platform:fasti-engine-cdi-jakarta` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-rest` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-rest` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-webapp` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-webapp` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-security` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-security` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-external-task-client` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-external-task-client` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-test` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-test` |
| `org.camunda.bpm:camunda-engine-rest-core` | `dev.fasti.platform:fasti-engine-rest-core` |
| `org.camunda.bpm:camunda-engine-rest-core-jakarta` | `dev.fasti.platform:fasti-engine-rest-core-jakarta` |
| `org.camunda.bpm.webapp:camunda-webapp` | `dev.fasti.platform.webapp:fasti-webapp` |
| `org.camunda.bpm.webapp:camunda-webapp-jakarta` | `dev.fasti.platform.webapp:fasti-webapp-jakarta` |
| `org.camunda.bpm.webapp:camunda-webapp-webjar` | `dev.fasti.platform.webapp:fasti-webapp-webjar` |
| `org.camunda.bpm:camunda-external-task-client` | `dev.fasti.platform:fasti-external-task-client` |
| `org.camunda.bpm:camunda-engine-plugin-spin` | `dev.fasti.platform:fasti-engine-plugin-spin` |
| `org.camunda.bpm:camunda-engine-plugin-connect` | `dev.fasti.platform:fasti-engine-plugin-connect` |
| `org.camunda.spin:camunda-spin-dataformat-all` | `dev.fasti.platform.spin:fasti-spin-dataformat-all` |
| `org.camunda.connect:camunda-connect-connectors-all` | `dev.fasti.platform.connect:fasti-connect-connectors-all` |
| `org.camunda.bpm.dmn:camunda-engine-dmn` | `dev.fasti.platform.dmn:fasti-engine-dmn` |
| `org.camunda.bpm.model:camunda-bpmn-model` | `dev.fasti.platform.model:fasti-bpmn-model` |
| `org.camunda.bpm:camunda-bpm-assert` | `dev.fasti.platform:fasti-bpm-assert` |
| `org.camunda.bpm:camunda-bpm-junit5` | `dev.fasti.platform:fasti-bpm-junit5` |

FEEL लाइब्रेरी `org.camunda.feel:feel-engine` एक बाहरी लाइब्रेरी है जो इस
रिपॉज़िटरी का हिस्सा नहीं है; इसके निर्देशांक यथावत रहते हैं।

<a id="third-party-libraries-that-still-pull-in-the-original-engine"></a>

### तृतीय-पक्ष लाइब्रेरी जो अब भी मूल इंजन को डिपेंडेंसी के रूप में लाती हैं

पैकेज अपरिवर्तित होने के कारण, मूल इंजन (`org.camunda.bpm:camunda-engine`)
और `fasti-engine` में समान नाम वाली क्लास हैं। Maven इन्हें दो असंबंधित
लाइब्रेरी मानता है और किसी को भी नहीं हटाता। यदि कोई तृतीय-पक्ष लाइब्रेरी (उदाहरण के लिए कोई सामुदायिक एक्सटेंशन)
मूल आर्टिफ़ैक्ट पर निर्भर करती है, तो उन्हें बाहर करें और उनके स्थान पर संबंधित
Fasti आर्टिफ़ैक्ट घोषित करें:

```xml
<dependency>
  <groupId>com.example</groupId>
  <artifactId>some-camunda-extension</artifactId>
  <version>1.2.3</version>
  <exclusions>
    <exclusion>
      <groupId>org.camunda.bpm</groupId>
      <artifactId>*</artifactId>
    </exclusion>
    <exclusion>
      <groupId>org.camunda.bpm.model</groupId>
      <artifactId>*</artifactId>
    </exclusion>
    <exclusion>
      <groupId>org.camunda.bpm.dmn</groupId>
      <artifactId>*</artifactId>
    </exclusion>
    <exclusion>
      <groupId>org.camunda.commons</groupId>
      <artifactId>*</artifactId>
    </exclusion>
  </exclusions>
</dependency>
```

`mvn dependency:tree -Dincludes="org.camunda*"` अब भी मौजूद मूल आर्टिफ़ैक्ट की
सूची देता है (`org.camunda.feel:feel-engine` का होना उचित है, इसे बाहर न करें)।

<a id="startup-check"></a>

### स्टार्टअप जाँच

बनाए जाते समय इंजन क्लासपाथ की जाँच करता है और यदि उसे एक से अधिक लाइब्रेरी में
प्रोसेस इंजन की क्लास मिलती हैं, तो वह **शुरू होने से इनकार करता है**; त्रुटि `ENGINE-12021`
हर क्लास का स्थान सूचीबद्ध करती है। Spring Boot स्टार्टर भी एप्लिकेशन शुरू होते ही
यही जाँच करता है। केवल पैरेंट क्लास लोडर के माध्यम से दिखाई देने वाली लाइब्रेरी (उदाहरण के लिए वेब एप्लिकेशन में एम्बेडेड
इंजन के साथ सर्वर का साझा इंजन) की गणना नहीं की
जाती। यदि आपका सेटअप जानबूझकर ऐसा है, तो सिस्टम प्रॉपर्टी से यह जाँच बंद की जा सकती है:

```
-Dfasti.engine.duplicate-library-check.enabled=false
```

सीमा: यदि क्लासपाथ पर मूल इंजन लाइब्रेरी `fasti-engine` से पहले आती है, तो
इंजन मूल लाइब्रेरी की क्लास से बनता है, जिनमें यह जाँच नहीं है।
उस स्थिति में केवल Fasti Spring Boot स्टार्टर का उपयोग करने वाले एप्लिकेशन रोके जाते हैं; अन्य
इंटीग्रेशन के लिए ऊपर बताए अनुसार डिपेंडेंसी की समीक्षा करें।

<a id="web-application-path-fasti"></a>

## वेब एप्लिकेशन पथ: `/fasti`

प्रत्येक वितरण में Cockpit, Tasklist, Admin और नया वेब UI `/fasti` (पहले `/camunda`) के अंतर्गत
उपलब्ध हैं: `http://localhost:8080/fasti/` नया UI
(`/fasti/app/next/default/`) खोलता है, क्लासिक एप्लिकेशन `/fasti/app/cockpit/...`,
`/fasti/app/tasklist/...` और `/fasti/app/admin/...` पर हैं। REST API `/engine-rest` पर ही रहती है।

**क्लासिक एप्लिकेशन अप्रचलित घोषित किए गए हैं।** क्लासिक Cockpit, Tasklist, Admin और Welcome,
AngularJS 1.8.2 और Bootstrap 3 पर बने हैं, जिन दोनों का जीवनचक्र समाप्त हो चुका है। **कंटेनर इमेज इन्हें
डिफ़ॉल्ट रूप से उपलब्ध नहीं कराती** (संगतता तोड़ने वाला बदलाव): इनके पृष्ठ और पुराने बुकमार्क नए UI के संबंधित अनुभाग पर रीडायरेक्ट होते हैं, और इनकी फ़ाइलों के अनुरोध `404` लौटाते हैं; `FASTI_CLASSIC_WEBAPPS=true` इन्हें
फिर से उपलब्ध करता है (चेतावनी के साथ), [distro/docker/README.md](distro/docker/README.md) देखें।
Tomcat और WildFly बंडल भी इन्हें डिफ़ॉल्ट रूप से उपलब्ध नहीं कराते (इन्हें फिर से उपलब्ध करने के लिए
`-Dfasti.webapp.classic.enabled=true` या `FASTI_CLASSIC_WEBAPPS=true` का उपयोग करें)। Run zip और Spring
Boot स्टार्टर इन्हें पहले की तरह उपलब्ध रखते हैं, ताकि चल रही स्थापनाएँ बाधित न हों; इन्हें
`fasti.webapp.classic.enabled: false` से बंद करें। क्लासिक एप्लिकेशन बाद की किसी
रिलीज़ में हटा दिए जाएँगे।

पुराना पथ काम करता रहता है: `/camunda/...` के प्रत्येक अनुरोध को `/fasti/...` के अंतर्गत उसी पथ पर
रीडायरेक्ट किया जाता है, और पथ का शेष भाग तथा क्वेरी स्ट्रिंग बरकरार रहती है। GET और HEAD को
`301` मिलता है; अन्य सभी विधियों (POST, PUT, DELETE, उदाहरण के लिए `/camunda/api/...` कॉल करने वाले पुराने क्लाइंट)
को `308` मिलता है, ताकि क्लाइंट उसी विधि और बॉडी के साथ अनुरोध दोहराएँ। रीडायरेक्ट में
`Cache-Control: no-store` होता है, इसलिए `/camunda` पर वापस स्विच करना तुरंत प्रभावी होता है।

`/camunda` को पुनर्स्थापित करने के लिए:

- **Spring Boot, Run:** `camunda.bpm.webapp.application-path: /camunda` (तब कोई रीडायरेक्ट नहीं)।
  केवल रीडायरेक्ट बंद करने के लिए: `fasti.webapp.legacy-path-redirect: false`।
- **Tomcat:** `webapps/camunda` (रीडायरेक्ट एप्लिकेशन) हटाएँ और
  `webapps/fasti` का नाम बदलकर `webapps/camunda` करें।
- **WildFly:** `standalone/deployments/camunda.war` (और `camunda.war.dodeploy`) हटाएँ और
  वेब एप्लिकेशन WAR के `WEB-INF/jboss-web.xml` में
  `<context-root>/camunda</context-root>` सेट करें।

**रिवर्स प्रॉक्सी:** यदि कोई प्रॉक्सी केवल `/camunda/` फ़ॉरवर्ड करती है (या पथ दोबारा लिखती है, `/camunda` के लिए कुकी
पथ, CSP या हेल्थ चेक सेट करती है), तो `/fasti/` जोड़ें या पूरी तरह `/fasti/` पर जाएँ।
`XSRF-TOKEN` कुकी (और Tomcat तथा WildFly पर `JSESSIONID`) का पथ अब `/fasti` है,
इसलिए उपयोगकर्ताओं को दोबारा साइन इन करना होगा। बुकमार्क और पुराने लिंक रीडायरेक्ट के माध्यम से काम करते रहते हैं।

<a id="single-sign-on-openid-connect"></a>

## सिंगल साइन-ऑन (OpenID Connect)

**Run** (`./start.sh --oauth2`, मॉड्यूल `internal/oauth2`) और
`fasti-bpm-spring-boot-starter-security` वाले **Spring Boot** एप्लिकेशन पर समर्थित। इसे
Spring Security क्लाइंट पंजीकरण (`spring.security.oauth2.client.*`) घोषित करके सक्षम किया जाता है। **Tomcat** और
**WildFly** के लिए इस अनुभाग का अंत देखें।

- **लॉगिन:** नए वेब एप्लिकेशन का लॉगिन पृष्ठ पासवर्ड फ़ॉर्म के बजाय "&lt;provider&gt; से साइन इन करें"
  दिखाता है (लेबल: `fasti.security.oidc.login-label`, डिफ़ॉल्ट
  `client-name`; कई पंजीकरण होने पर
  `fasti.security.oidc.registration-id` से एक चुनें)। ऑथराइज़ेशन कोड फ़्लो में `state`, `nonce`
  और PKCE (S256, गोपनीय क्लाइंट के लिए भी) का उपयोग होता है; लॉगिन पर सत्र ID बदल दी जाती है। उपयोगकर्ता
  नए वेब एप्लिकेशन के उसी पृष्ठ पर लौटते हैं जहाँ से उन्होंने शुरुआत की थी (केवल
  `/fasti/app/next/` के अंतर्गत पथ स्वीकार किए जाते हैं, इसलिए कोई ओपन रीडायरेक्ट नहीं है)।
- **लॉगआउट:** `POST /logout` (केवल समान मूल के अनुरोध) सत्र समाप्त करता है;
  `camunda.bpm.oauth2.sso-logout.enabled=true` होने पर उपयोगकर्ता प्रदाता के सत्र समाप्ति
  एंडपॉइंट पर जाता है (RP द्वारा शुरू किया गया लॉगआउट, `post-logout-redirect-uri`)। Spring Security का
  बैक-चैनल लॉगआउट सक्षम रहता है। `GET /logout` अब उपयोगकर्ता को लॉगआउट नहीं करता।
- **सत्र की समय-सीमा समाप्त होना:** जब एक्सेस टोकन रिफ़्रेश नहीं हो सकता या सत्र का समय समाप्त होता है,
  `/fasti/api/**` के अनुरोध `401` लौटाते हैं (कोई रीडायरेक्ट नहीं) और नया वेब एप्लिकेशन अपना लॉगिन पृष्ठ दिखाता है।
- **CSRF:** `/fasti/api` और `/fasti/app` वेब एप्लिकेशन का अपना CSRF फ़िल्टर
  (`X-XSRF-TOKEN`) बनाए रखते हैं; अपस्ट्रीम की तरह Spring Security की CSRF सुरक्षा बंद रहती है।
- **समूह, व्यवस्थापक, टेनेंट:** समूह क्लेम
  `camunda.bpm.oauth2.identity-provider.group-name-attribute` है (अपस्ट्रीम प्रॉपर्टी नाम;
  `realm_access.roles` जैसे नेस्टेड क्लेम काम करते हैं)। `fasti.security.oidc.groups.admin` उन
  क्लेम मानों की सूची देता है जो `camunda-admin` प्रदान करते हैं; `groups.mapping` क्लेम मानों को इंजन समूह
  ID से मैप करता है; `groups.include-unmapped=false` बिना मैप किए मानों को हटा देता है। **अपस्ट्रीम से बदलाव:** क्लेम
  मान `camunda-admin` अब अपने आप व्यवस्थापक अधिकार नहीं देता, और
  `SCOPE_openid` या `OIDC_USER` जैसे अधिकार अब समूह नहीं बनते।
  `fasti.security.oidc.tenants.claim` टेनेंट को मैप करता है। नाम और ईमेल:
  `fasti.security.oidc.user.{first-name,last-name,email}-claim`।
- **पहचान प्रदाता:** डिफ़ॉल्ट रूप से इंजन वर्तमान सत्र के टोकन से उपयोगकर्ता, समूह और टेनेंट पढ़ता है
  (केवल पढ़ने के लिए)। इसके बजाय डेटाबेस का उपयोग करने के लिए
  `camunda.bpm.oauth2.identity-provider.enabled=false` सेट करें (तब समूह
  सदस्यता टेबल से आते हैं)। ऑटो-प्रोविज़निंग लागू नहीं की गई है।
  जब उपयोगकर्ता टोकन से आते हैं (डिफ़ॉल्ट), तो प्रारंभिक सेटअप पृष्ठ (पहला व्यवस्थापक)
  बंद रहता है, क्योंकि व्यवस्थापक क्लेम (`groups.admin`) से आते हैं; पहले, व्यवस्थापक समूह से बाहर के उपयोगकर्ताओं को सेटअप पृष्ठ पर भेजा जाता था।
- **बियरर टोकन के साथ REST API:** `fasti.security.oidc.rest.bearer-enabled=true`, Basic प्रमाणीकरण के अतिरिक्त
  `/engine-rest` पर `Authorization: Bearer <JWT>` स्वीकार करता है।
  `rest.issuer-uri` और `rest.audiences` आवश्यक हैं; हस्ताक्षरों की जाँच जारीकर्ता के
  JWKS से केवल असममित एल्गोरिदम (`rest.jws-algorithms`, डिफ़ॉल्ट `RS256`) द्वारा की जाती है, और
  `iss`, `aud`, `exp`, `nbf` सत्यापित किए जाते हैं। Run में `camunda.bpm.run.auth.enabled=true` भी आवश्यक है।
  ब्राउज़र सत्र कुकी कभी भी `/engine-rest` को प्रमाणित नहीं करती।

Keycloak उदाहरण (Run का `configuration/default.yml`):

```yaml
spring.security.oauth2.client:
  registration.keycloak:
    client-id: fasti
    client-secret: ${KEYCLOAK_CLIENT_SECRET}
    client-name: Keycloak
    scope: openid, profile, email
  provider.keycloak:
    issuer-uri: https://sso.example.com/realms/fasti
    user-name-attribute: preferred_username
camunda.bpm:
  oauth2:
    # the client's "Group Membership" mapper, with "Full group path" off
    identity-provider.group-name-attribute: groups
    sso-logout:
      enabled: true
      post-logout-redirect-uri: "{baseUrl}/fasti/app/next/default/"
  run.auth.enabled: true
fasti.security.oidc:
  groups.admin: [fasti-admins]
  rest:
    bearer-enabled: true
    issuer-uri: https://sso.example.com/realms/fasti
    audiences: [fasti-api]          # "Audience" mapper in Keycloak
    user-id-claim: preferred_username
```

Microsoft Entra ID उदाहरण:

```yaml
spring.security.oauth2.client:
  registration.entra:
    client-id: <application-id>
    client-secret: ${ENTRA_CLIENT_SECRET}
    client-name: Microsoft
    scope: openid, profile, email
  provider.entra:
    issuer-uri: https://login.microsoftonline.com/<tenant-id>/v2.0
    user-name-attribute: preferred_username
camunda.bpm.oauth2:
  # app registration manifest: "groupMembershipClaims": "SecurityGroup"
  identity-provider.group-name-attribute: groups
  sso-logout.enabled: true
fasti.security.oidc:
  groups:
    admin: ["<object id of the administrators group>"]
    mapping:
      "<object id of the accounting group>": accounting
    include-unmapped: false          # Entra sends object ids, not group names
  rest:
    bearer-enabled: true
    issuer-uri: https://login.microsoftonline.com/<tenant-id>/v2.0
    audiences: ["api://<application-id>"]   # v2 access tokens: accessTokenAcceptedVersion = 2
    user-id-claim: preferred_username
```

**Tomcat, WildFly (WAR):** वही सुविधाएँ, जिन्हें `fasti-oidc.properties` में कॉन्फ़िगर किया जाता है (Tomcat:
`conf/`, WildFly: `standalone/configuration/`; व्याख्यात्मक टिप्पणियों वाली `fasti-oidc.properties.example`
फ़ाइल इसके साथ ही है)। फ़ाइल के बिना कुछ नहीं बदलता। कुंजियाँ Run की `fasti.security.oidc.*` प्रॉपर्टी से मेल खाती हैं, और इनके अतिरिक्त `issuer-uri`, `client-id`, `client-secret`, `user-name-claim`, `groups-claim` हैं।
प्रदाता को रीडायरेक्ट URI `<base>/fasti/sso/callback` की अनुमति देनी होगी; बैक-चैनल लॉगआउट URL
`<base>/fasti/sso/backchannel-logout` है। `/engine-rest` पर बियरर टोकन
`fasti.security.oidc.rest.bearer-enabled=true` से सक्षम होते हैं (Basic प्रमाणीकरण तब तक काम करता रहता है जब तक
`rest.basic-enabled=false` न हो; इंजन सूची `/engine` के लिए भी क्रेडेंशियल आवश्यक हैं)। उपयोगकर्ताओं का
डेटाबेस में मौजूद होना आवश्यक नहीं है। साइन इन किए हुए उपयोगकर्ता के समूहों और टेनेंट से जुड़ी क्वेरी का
उत्तर इंजन क्लेम से दे सके, इसके लिए `OidcIdentityProviderPlugin` सक्षम करें (`bpm-platform.xml` / `standalone.xml`
में टिप्पणी के रूप में निष्क्रिय है)। सीमाएँ: बैक-चैनल लॉगआउट के लिए ट्रैक किए गए सत्र
प्रत्येक सर्वर की मेमोरी में रखे जाते हैं (क्लस्टर नोड के बीच साझा नहीं होते); क्लासिक एप्लिकेशन केवल प्रदाता पर रीडायरेक्ट करते हैं; पूर्ण लॉगआउट के लिए नया वेब UI आवश्यक है।

<a id="build"></a>

## बिल्ड

JDK 17 या बाद का संस्करण आवश्यक है (21 अनुशंसित)।

```bash
./mvnw clean verify
```

पूरा बिल्ड सभी Java परीक्षण और वेब UI यूनिट परीक्षण चलाता है (लगभग एक घंटा; Playwright
एंड-टू-एंड परीक्षण अलग से चलते हैं, [webapps/frontend-next](webapps/frontend-next/README.md#end-to-end-tests-playwright) देखें) और इसके लिए Maven Central,
nodejs.org तथा npm रजिस्ट्री तक नेटवर्क पहुँच आवश्यक है। [TESTING.md](TESTING.md) देखें।

<a id="acknowledgements"></a>

## आभार

- Fasti BPM Platform, Camunda और Camunda 7 समुदाय के एक दशक से अधिक के
  काम पर आधारित है।
- दोषों की समीक्षा करते समय प्रोजेक्ट ने Camunda 7 से निकले अन्य सामुदायिक प्रोजेक्ट की
  सार्वजनिक जानकारी (इश्यू, सुरक्षा सलाह, रिलीज़ नोट) का संदर्भ लिया: Operaton, CIB
  seven, OrqueIO और EximeeBPMS। इस रिपॉज़िटरी का सोर्स कोड Camunda 7 CE के आधार पर
  स्वतंत्र रूप से लिखा गया था।

<a id="license"></a>

## लाइसेंस

[Apache License 2.0](LICENSE)। मूल कोड के कॉपीराइट के लिए [सूचना](NOTICE) देखें।
