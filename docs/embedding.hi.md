[English](embedding.md) | [Tiếng Việt](embedding.vi.md) | [Deutsch](embedding.de.md) | [Español](embedding.es.md) | [Français](embedding.fr.md) | हिन्दी | [Bahasa Indonesia](embedding.id.md) | [日本語](embedding.ja.md) | [한국어](embedding.ko.md) | [Português (Brasil)](embedding.pt-BR.md) | [Русский](embedding.ru.md) | [ไทย](embedding.th.md) | [简体中文](embedding.zh-CN.md)

> यह अनुवाद समुदाय द्वारा बनाए रखा जाता है और अंग्रेज़ी संस्करण से पीछे हो सकता है; अंग्रेज़ी संस्करण ही प्रामाणिक है। सुधारों का स्वागत है।

# Fasti BPM को एम्बेड करना: संगतता और Maven निर्देशांक

Fasti, Camunda 7 से क्या बनाए रखता है और 7.24.0 से क्या अलग है, संस्करण कैसे क्रमांकित होते हैं, और इंजन को एम्बेड करने वाले एप्लिकेशन के लिए Maven निर्देशांक। Fasti को सर्वर के रूप में चलाने के लिए [Docker के साथ स्थापना](install-docker.hi.md) या <https://fasti.dev/download> पर Tomcat बंडल देखें।

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
[माइग्रेशन गाइड](migrate-from-camunda-7.hi.md#what-differs) में "क्या अलग है" पढ़ें।

<a id="versioning"></a>

## संस्करण निर्धारण

Fasti BPM Platform, `1.0.0` से शुरू करते हुए [सिमैंटिक वर्ज़निंग](https://semver.org/) का पालन करता है।
`1.x` शृंखला Camunda 7.24 का Java API, `org.camunda.bpm.*` पैकेज, `camunda.bpm.*`
कॉन्फ़िगरेशन प्रीफ़िक्स और डेटाबेस स्कीमा बनाए रखती है। इंजन को एम्बेड करने वाले एप्लिकेशन Java 17
या बाद के संस्करण पर और, Spring Boot स्टार्टर के साथ, Spring Boot 4.1 पर जाते हैं; प्लगइन और
प्रोसेस एप्लिकेशन `dev.fasti.platform` आर्टिफ़ैक्ट के विरुद्ध फिर से बिल्ड किए जाते हैं।

<a id="maven-coordinates"></a>

## Maven निर्देशांक

Fasti BPM Platform के आर्टिफ़ैक्ट के अपने Maven निर्देशांक हैं। **Java पैकेज
`org.camunda.bpm.*` ही रहते हैं**, इसलिए एप्लिकेशन कोड के इम्पोर्ट बरकरार रहते हैं; `camunda.bpm.*`
कॉन्फ़िगरेशन प्रीफ़िक्स, BPMN/DMN नेमस्पेस, डेटाबेस स्कीमा और
WildFly पर मॉड्यूल के नाम भी अपरिवर्तित हैं। Maven के संदर्भ में केवल डिपेंडेंसी घोषणाएँ
बदलती हैं (वेब एप्लिकेशन पथ अलग से बदलता है,
[वेब एप्लिकेशन पथ](web-ui.hi.md#web-application-path-fasti) देखें):

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
