[English](README.md) | [Tiếng Việt](README.vi.md) | [Deutsch](README.de.md) | [Español](README.es.md) | [Français](README.fr.md) | हिन्दी | [Bahasa Indonesia](README.id.md) | [日本語](README.ja.md) | [한국어](README.ko.md) | [Português (Brasil)](README.pt-BR.md) | [Русский](README.ru.md) | [ไทย](README.th.md) | [简体中文](README.zh-CN.md)

> यह अनुवाद समुदाय द्वारा बनाए रखा जाता है और अंग्रेज़ी संस्करण से पीछे हो सकता है; अंग्रेज़ी संस्करण ही प्रामाणिक है। सुधारों का स्वागत है।

# Fasti BPM Platform

Fasti BPM एक स्वतंत्र, open-source engine है जो Camunda 7 Community Edition 7.24.0 पर बना है।
यह आपके मौजूदा Camunda 7 database पर चलता है, और इसमें मौजूदा libraries, security fixes और एक
नया वेब इंटरफ़ेस है। वेबसाइट: <https://fasti.dev>

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
  fastidev/fasti-bpm:1.0.1
```

<http://localhost:8080/fasti/> खोलें और साइन इन करें। `DB_URL` के बिना इमेज कंटेनर के भीतर H2 डेटाबेस
का उपयोग करती है, जो केवल मूल्यांकन के लिए है। REST API के लिए Basic क्रेडेंशियल आवश्यक हैं:

```bash
curl -i http://localhost:8080/engine-rest/task      # 401: no credentials
curl -u admin:'change-me' http://localhost:8080/engine-rest/task
```

पूरी गाइड, अपने स्वयं के डेटाबेस के उपयोग सहित: <https://fasti.dev/install>

<a id="highlights"></a>

## प्रमुख विशेषताएँ

- **Connection बदलिए। यही migration है।** Fasti Camunda 7.24 schema को जैसा है वैसा ही इस्तेमाल
  करता है; `org.camunda.bpm.*` packages वही रहते हैं ([migration](https://fasti.dev/install#migrate))।
- **एक container, एक command**, Camunda Run जैसे ही `DB_*` variables के साथ ([इंस्टॉल](https://fasti.dev/install))।
- **उन लोगों के लिए बना जो रोज़ काम की कतार निपटाते हैं**: एक नया वेब इंटरफ़ेस, 13 भाषाओं में,
  लाइट और डार्क, और फ़ोन पर उपयोगी ([सुविधाएँ](https://fasti.dev/#features))।
- **Browser में model और deploy करें**: BPMN, DMN और forms ([मॉडलर](https://fasti.dev/#modeler))।
- **इसे चलाने के tools साथ ही मिलते हैं**: history, instance की मरम्मत, bulk retry, operation log
  ([संचालन](https://fasti.dev/#operate))।
- **कंटेनर image में default रूप से बंद**: पहली शुरुआत से sign-in ज़रूरी, कोई डिफ़ॉल्ट यूज़र
  नहीं, REST authentication या authorizations बंद करने वाली settings अस्वीकार की जाती हैं, और
  Java4Shell का mitigation। Tomcat और WildFly REST API के लिए credentials माँगते हैं और कोई यूज़र
  नहीं बनाते (पहला administrator setup page पर बनाया जाता है); Run zip और Spring Boot starter
  Camunda 7 के defaults रखते हैं। हर distribution में security fixes हैं और Java4Shell से
  प्रभावित JVM पर startup के समय चेतावनी दी जाती है ([सुरक्षा](https://fasti.dev/security))।
- **Single sign-on**: OpenID Connect और JWT bearer tokens ([गाइड](docs/single-sign-on.hi.md))।
- **वर्तमान प्लेटफ़ॉर्म**: Java 17 या बाद का संस्करण, Spring Boot 4.1, GraalJS 25, Jackson 2.21
  ([प्रदर्शन](https://fasti.dev/#performance))।

<a id="installation"></a><a id="get-fasti"></a>

## Fasti प्राप्त करें

- <a id="one-command-on-your-existing-camunda-724-database"></a>**Docker इमेज** (`fastidev/fasti-bpm`, amd64 और arm64): <https://fasti.dev/install>; चरण-दर-चरण
  निर्देश [docs/install-docker.hi.md](docs/install-docker.hi.md) में, सभी वेरिएबल
  [distro/docker/README.md](distro/docker/README.md#environment-variables) में।
- <a id="without-docker-tomcat-bundle-download-from-fastidev"></a>**Tomcat बंडल**, Docker के बिना: <https://fasti.dev/download>। तैयार Tomcat 1.0.0 बंडल:
  - [fasti-bpm-tomcat-1.0.0.zip](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.zip) ([SHA-256](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.zip.sha256))
  - [fasti-bpm-tomcat-1.0.0.tar.gz](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.tar.gz) ([SHA-256](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.tar.gz.sha256))

  एक्सट्रैक्ट करने से पहले चेकसम सत्यापित करें, उदाहरण के लिए `sha256sum -c fasti-bpm-tomcat-1.0.0.zip.sha256`,
  और शुरू करने से पहले बंडल में `README.txt` पढ़ें। यह बंडल अभी भी संस्करण 1.0.0 है और इसमें
  1.0.1 के दो security fixes (template filter, LDAP certificates) नहीं हैं; उन्हें पाने के लिए
  image का उपयोग करें या `v1.0.1` tag से Tomcat distribution (`distro/tomcat`) बिल्ड करें।
- **एम्बेडेड**, Spring Boot या किसी अन्य Java एप्लिकेशन में: नए Maven निर्देशांक
  (`dev.fasti.platform:fasti-*`), वही `org.camunda.bpm.*` पैकेज;
  [docs/embedding.hi.md](docs/embedding.hi.md) देखें।

<a id="moving-from-camunda-7"></a>

## Camunda 7 से आएँ

अगर आप Camunda 7.24 चलाते हैं, तो Fasti उसी database पर उसका schema बदले बिना चलता है। आप पुराने
nodes को बदल सकते हैं, Fasti को उनके साथ चला सकते हैं, और किसी भी समय Camunda 7.24 पर वापस जा सकते
हैं। Fasti image को PostgreSQL 16, MariaDB 11.8, MySQL 8.4, SQL Server 2022 और Oracle Database 23ai
पर Camunda Run 7.24 द्वारा बनाए गए databases को take over करते हुए test किया गया है; Camunda Run
पर वापस जाना MySQL को छोड़कर इन्हीं databases पर test किया गया है। DB2 अभी test नहीं हुआ है। हर REST client को, external task workers सहित, अब Basic credentials या bearer token
भेजना होगा।

Embedded Spring Boot applications भी Spring Boot 4.1 और Java 17 पर जाती हैं; पुराने schemas को पहले
standard scripts से upgrade किया जाता है। Camunda 7 के लिए बने plugins और process applications को
Fasti के भीतर चलाने के लिए Fasti artifacts के विरुद्ध दोबारा build करना होगा। साथ-साथ चलाने और वापस
जाने की सीमाएँ हैं (हर node पर समान configuration, कौन-सा node jobs चलाता है, Fasti की विशेष
सुविधाओं वाले processes): देखें
[मिश्रित संचालन](docs/migrate-from-camunda-7.hi.md#mixed-operation-with-camunda-7-nodes) और
[रोलबैक](docs/migrate-from-camunda-7.hi.md#rollback-to-camunda-724)।

Migration के चरण और सीमाएँ: <https://fasti.dev/install#migrate> और
[माइग्रेशन गाइड](docs/migrate-from-camunda-7.hi.md) (रोलबैक, पुराने स्कीमा, परीक्षण परिणाम)।

<a id="comparison"></a><a id="how-it-compares"></a>

## तुलना

कई अच्छे projects Camunda 7 को आगे ले जा रहे हैं। Fasti `org.camunda.bpm.*` packages और 7.24
schema को बनाए रखता है, इसलिए यह बदलाव migration project नहीं, बस connection बदलना है, और साथ
में नया interface मिलता है। दूसरे projects भी अच्छे विकल्प हैं। Sources के साथ पूरी table:
<https://fasti.dev/compare>।

<a id="commitments-support-and-security-patches"></a><a id="security-and-support"></a>

## सुरक्षा और support

`1.x` line की नवीनतम minor release को bug fixes और security patches मिलते हैं, `1.0.1` जैसी
patch releases के रूप में। कृपया security समस्याएँ निजी तौर पर report करें, किसी public issue में
नहीं: [SECURITY.md](SECURITY.md#security-policy) देखें, जिसमें
[सहायता नीति](SECURITY.md#support-policy) भी है। सुधारों की सूची, Java4Shell और pre-release
समीक्षा: <https://fasti.dev/security>। इंस्टॉलेशन, माइग्रेशन या प्रोडक्शन में मदद और सहायता की
शर्तों के लिए <contact@fasti.dev> पर लिखें।

<a id="timeline"></a><a id="camunda-7-milestones"></a><a id="fasti-bpm"></a><a id="roadmap-plan"></a>

## रोडमैप

यह एक योजना है, तारीख़ों का वादा नहीं। `1.x` में आगे: Maven Central पर Maven आर्टिफ़ैक्ट, Helm
chart, Prometheus metrics और OpenTelemetry tracing, structured JSON logs। `2.0` के लिए: अनुपस्थिति
में substitute, approvals के लिए four-eyes control, immutable audit trail, और नए इंटरफ़ेस में
CMMN। Camunda 7 CE के अंत से 2027 के लक्ष्यों तक की टाइमलाइन:
<https://fasti.dev/#timeline>।

<a id="documentation"></a>

## दस्तावेज़

[fasti.dev](https://fasti.dev) पर: [Docker से इंस्टॉल करें](https://fasti.dev/install) ·
[Tomcat डाउनलोड](https://fasti.dev/download) · [API integration गाइड](https://fasti.dev/docs) ·
[सुरक्षा और support](https://fasti.dev/security) · [तुलना](https://fasti.dev/compare) ·
[ब्लॉग](https://fasti.dev/blog)

<a id="what-this-is"></a><a id="what-is-different-from-camunda-7240-ce"></a><a id="versioning"></a><a id="maven-coordinates"></a><a id="third-party-libraries-that-still-pull-in-the-original-engine"></a><a id="startup-check"></a><a id="web-application-path-fasti"></a><a id="single-sign-on-openid-connect"></a><a id="a-ui-built-from-real-user-complaints"></a>

| दस्तावेज़ | विषय-वस्तु |
|---|---|
| [docs/install-docker.md](docs/install-docker.hi.md) | Docker के साथ चरण-दर-चरण स्थापना |
| [docs/migrate-from-camunda-7.md](docs/migrate-from-camunda-7.hi.md) | Camunda 7 से माइग्रेशन, रोलबैक, सीमाएँ |
| [docs/embedding.md](docs/embedding.hi.md) | संगतता, 7.24.0 से क्या अलग है, संस्करण निर्धारण, Maven निर्देशांक, स्टार्टअप जाँच |
| [docs/web-ui.md](docs/web-ui.hi.md) | वेब इंटरफ़ेस: उपयोगकर्ताओं द्वारा माँगी गई सुविधाएँ, `/fasti` पथ, क्लासिक ऐप्स |
| [docs/single-sign-on.md](docs/single-sign-on.hi.md) | सिंगल साइन-ऑन (OpenID Connect): Run, Spring Boot, Tomcat, WildFly |
| [docs/api/README.md](docs/api/README.md) | REST API इंटीग्रेशन, Java और JavaScript एक्सटर्नल टास्क क्लाइंट |
| [distro/docker/README.md](distro/docker/README.md) | इमेज: एनवायरनमेंट वेरिएबल, ड्राइवर, संचालन, ट्यूनिंग |
| [webapps/frontend-next/README.md](webapps/frontend-next/README.md#new-web-ui-frontend-next) | नया वेब UI |
| [docs/security/CVE-2026-114938.md](docs/security/CVE-2026-114938.md#cve-2026-114938-java4shell-and-fasti-bpm) | Java4Shell और Fasti |
| [CHANGELOG.md](CHANGELOG.md), [SECURITY.md](SECURITY.md#security-policy) | बदलाव, सुरक्षा, सहायता नीति |

<a id="build"></a>

## सोर्स से बिल्ड

JDK 17 या बाद का संस्करण आवश्यक है (21 अनुशंसित), साथ ही Maven Central, nodejs.org तथा npm
रजिस्ट्री तक नेटवर्क पहुँच (बिल्ड वेब UI के लिए Node/npm डाउनलोड करता है)।

```bash
./mvnw clean verify
```

पूरा बिल्ड सभी Java परीक्षण और वेब UI यूनिट परीक्षण चलाता है (लगभग एक घंटा; Playwright
एंड-टू-एंड परीक्षण अलग से चलते हैं, [webapps/frontend-next](webapps/frontend-next/README.md#end-to-end-tests-playwright) देखें)।
[TESTING.md](TESTING.md) देखें। कंटेनर इमेज बनाने के लिए (पहले बिल्ड में लगभग 10–20 मिनट
लगते हैं):

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
```

<a id="contributing"></a>

## योगदान

प्रश्न, बग रिपोर्ट और pull request GitHub पर स्वागत योग्य हैं;
[CONTRIBUTING.md](CONTRIBUTING.md#how-to-contribute) और [आचार संहिता](CODE_OF_CONDUCT.md) देखें।
मातृभाषी लोगों द्वारा अनुवादों की समीक्षा का विशेष रूप से स्वागत है।

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
