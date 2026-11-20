[English](single-sign-on.md) | [Tiếng Việt](single-sign-on.vi.md) | [Deutsch](single-sign-on.de.md) | [Español](single-sign-on.es.md) | [Français](single-sign-on.fr.md) | हिन्दी | [Bahasa Indonesia](single-sign-on.id.md) | [日本語](single-sign-on.ja.md) | [한국어](single-sign-on.ko.md) | [Português (Brasil)](single-sign-on.pt-BR.md) | [Русский](single-sign-on.ru.md) | [ไทย](single-sign-on.th.md) | [简体中文](single-sign-on.zh-CN.md)

> यह अनुवाद समुदाय द्वारा बनाए रखा जाता है और अंग्रेज़ी संस्करण से पीछे हो सकता है; अंग्रेज़ी संस्करण ही प्रामाणिक है। सुधारों का स्वागत है।

<a id="single-sign-on-openid-connect"></a>

# सिंगल साइन-ऑन (OpenID Connect)

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
