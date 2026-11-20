[English](web-ui.md) | [Tiếng Việt](web-ui.vi.md) | [Deutsch](web-ui.de.md) | [Español](web-ui.es.md) | [Français](web-ui.fr.md) | हिन्दी | [Bahasa Indonesia](web-ui.id.md) | [日本語](web-ui.ja.md) | [한국어](web-ui.ko.md) | [Português (Brasil)](web-ui.pt-BR.md) | [Русский](web-ui.ru.md) | [ไทย](web-ui.th.md) | [简体中文](web-ui.zh-CN.md)

> यह अनुवाद समुदाय द्वारा बनाए रखा जाता है और अंग्रेज़ी संस्करण से पीछे हो सकता है; अंग्रेज़ी संस्करण ही प्रामाणिक है। सुधारों का स्वागत है।

# Fasti का वेब इंटरफ़ेस

नए वेब इंटरफ़ेस में ये सुविधाएँ क्यों हैं, और वेब एप्लिकेशन किस पथ पर उपलब्ध हैं। इंटरफ़ेस का परिचय: <https://fasti.dev/#features>।

<a id="a-ui-built-from-real-user-complaints"></a>

## वास्तविक उपयोगकर्ता शिकायतों के आधार पर बना UI

नए वेब UI की सुविधाएँ Camunda 7 उपयोगकर्ताओं द्वारा Camunda फ़ोरम और GitHub पर सार्वजनिक रूप से लिखी गई
बातों के आधार पर चुनी गईं, उदाहरण के लिए:

| उपयोगकर्ताओं ने क्या माँगा | स्रोत | Fasti में |
|---|---|---|
| OR के साथ कई वेरिएबल के आधार पर इंस्टेंस खोजें | फ़ोरम [39269](https://forum.camunda.io/t/39269), [45695](https://forum.camunda.io/t/45695), [48976](https://forum.camunda.io/t/48976), [64208](https://forum.camunda.io/t/64208), [28855](https://forum.camunda.io/t/28855), [4678](https://forum.camunda.io/t/4678); [camunda-bpm-platform#3236](https://github.com/camunda/camunda-bpm-platform/issues/3236) | कई वेरिएबल के लिए शर्तें बनाने का उपकरण, AND/OR |
| केस के हर चरण की टिप्पणियाँ देखें | फ़ोरम [30260](https://forum.camunda.io/t/30260), [52109](https://forum.camunda.io/t/52109), [2173](https://forum.camunda.io/t/2173) | टास्क में केस की समयरेखा; सीधे प्रोसेस इंस्टेंस पर टिप्पणियाँ |
| वेरिएबल के आधार पर टास्क क्रमबद्ध करें, सबसे पुराने पहले | फ़ोरम [17599](https://forum.camunda.io/t/17599), [42078](https://forum.camunda.io/t/42078), [41193](https://forum.camunda.io/t/41193), [67328](https://forum.camunda.io/t/67328) | वेरिएबल, नियत तिथियों और तात्कालिकता के आधार पर क्रमबद्ध करना |
| प्रत्यायोजन, अनुपस्थिति के दौरान स्थानापन्न | फ़ोरम [2788](https://forum.camunda.io/t/2788), [38199](https://forum.camunda.io/t/38199), [9331](https://forum.camunda.io/t/9331) | प्रति-कार्य प्रत्यायोजन, और हैंडओवर के साथ [अनुपस्थिति में स्थानापन्न](absence.md) |
| फ़ॉर्म के बिना प्रोसेस शुरू करते समय बिज़नेस की और वेरिएबल | [cibseven-webclient#528](https://github.com/cibseven/cibseven-webclient/issues/528) | हाँ |
| डिप्लॉय किया गया BPMN XML डाउनलोड करें | [camunda/camunda#22005](https://github.com/camunda/camunda/issues/22005) | हाँ |
| एकरूप लेबल, कई भाषाएँ | [operaton#643](https://github.com/operaton/operaton/issues/643) | 13 भाषाएँ; BPMN, DMN और फ़ॉर्म एडिटर के भीतर बिल्ट-इन लेबल केवल अंग्रेज़ी और वियतनामी में हैं |

<a id="web-application-path-fasti"></a>

## वेब एप्लिकेशन पथ: `/fasti`

प्रत्येक वितरण में Cockpit, Tasklist, Admin और नया वेब UI `/fasti` (पहले `/camunda`) के अंतर्गत
उपलब्ध हैं: `http://localhost:8080/fasti/` नया UI
(`/fasti/app/next/default/`) खोलता है, क्लासिक एप्लिकेशन `/fasti/app/cockpit/...`,
`/fasti/app/tasklist/...` और `/fasti/app/admin/...` पर हैं। REST API `/engine-rest` पर ही रहती है।

**क्लासिक एप्लिकेशन अप्रचलित घोषित किए गए हैं।** क्लासिक Cockpit, Tasklist, Admin और Welcome,
AngularJS 1.8.2 और Bootstrap 3 पर बने हैं, जिन दोनों का जीवनचक्र समाप्त हो चुका है। **कंटेनर इमेज इन्हें
डिफ़ॉल्ट रूप से उपलब्ध नहीं कराती** (संगतता तोड़ने वाला बदलाव): इनके पृष्ठ और पुराने बुकमार्क नए UI के संबंधित अनुभाग पर रीडायरेक्ट होते हैं, और इनकी फ़ाइलों के अनुरोध `404` लौटाते हैं; `FASTI_CLASSIC_WEBAPPS=true` इन्हें
फिर से उपलब्ध करता है (चेतावनी के साथ), [distro/docker/README.md](../distro/docker/README.md) देखें।
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
