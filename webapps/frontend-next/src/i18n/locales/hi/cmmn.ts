/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { Catalog } from '../../catalog';

/** Hindi messages of the pages of cases (CMMN). */
const messages: Catalog = {
  'cmmn.nav': 'केस (CMMN)',
  'cmmn.nav.history': 'केस इतिहास',

  'cmmn.definitions.title': 'केस परिभाषाएँ',
  'cmmn.definitions.count': '{count} केस परिभाषाएँ',
  'cmmn.definitions.count.one': '{count} केस परिभाषा',
  'cmmn.definitions.search': 'केस परिभाषाएँ खोजें',
  'cmmn.definitions.noMatch': 'खोज से कोई केस परिभाषा मेल नहीं खाती',
  'cmmn.definitions.empty': 'कोई केस परिभाषा डिप्लॉय नहीं की गई',
  'cmmn.definitions.emptyHint': 'केस CMMN में मॉडल किए जाते हैं और .cmmn फ़ाइलों के रूप में डिप्लॉय होते हैं, उदाहरण के लिए इस पेज पर',
  'cmmn.definitions.open': 'खुले इंस्टेंस',

  'cmmn.definition.title': 'केस परिभाषा',
  'cmmn.definition.notFound': 'यह केस परिभाषा मौजूद नहीं है',
  'cmmn.definition.instances': 'इस संस्करण के खुले इंस्टेंस',
  'cmmn.definition.instancesEmpty': 'इस संस्करण का कोई खुला इंस्टेंस नहीं है',
  'cmmn.definition.businessKeySearch': 'बिज़नेस कुंजी (सटीक)',
  'cmmn.definition.noMatch': 'किसी खुले इंस्टेंस की यह बिज़नेस कुंजी नहीं है',
  'cmmn.definition.diagramLabel': 'खुले प्लान आइटम की संख्या के साथ केस आरेख',
  'cmmn.definition.diagramHint': 'संख्याएँ बताती हैं कि खुले इंस्टेंस के कितने प्लान आइटम सक्रिय हैं या शुरू होने की प्रतीक्षा में हैं।',
  'cmmn.definition.truncated': 'आरेख की संख्याएँ केवल पहले {count} प्लान आइटम गिनती हैं।',

  'cmmn.create.action': 'केस इंस्टेंस बनाएँ',
  'cmmn.create.title': 'एक केस इंस्टेंस बनाएँ',
  'cmmn.create.text': '“{name}”, संस्करण {version} का एक इंस्टेंस बनाता है। इसके प्लान आइटम तुरंत शुरू होते हैं।',
  'cmmn.create.done': 'केस इंस्टेंस बनाया गया',
  'cmmn.create.failed': 'केस इंस्टेंस नहीं बनाया जा सका',

  'cmmn.instance': 'केस इंस्टेंस',
  'cmmn.instance.gone': 'यह केस इंस्टेंस मौजूद नहीं है',
  'cmmn.instance.goneHint': 'हो सकता है ID गलत हो, या इंस्टेंस बंद होकर उसका इतिहास हटा दिया गया हो।',
  'cmmn.instance.closedNote': 'यह केस इंस्टेंस बंद है। इसे वैसे दिखाया गया है जैसे इतिहास ने सहेजा था, और इसे बदला नहीं जा सकता।',
  'cmmn.instance.created': 'बनाया गया',
  'cmmn.instance.closedAt': 'बंद किया गया',
  'cmmn.instance.backToDefinition': 'केस परिभाषा पर वापस जाएँ',
  'cmmn.instance.parentProcess': 'एक प्रोसेस इंस्टेंस द्वारा शुरू किया गया',
  'cmmn.instance.parentCase': 'एक केस इंस्टेंस द्वारा शुरू किया गया',
  'cmmn.instance.diagramLabel': 'हर प्लान आइटम की स्थिति के साथ केस आरेख',
  'cmmn.instance.diagramHint': 'प्लान आइटम की सूची में केवल उसे देखने के लिए आरेख में एक प्लान आइटम चुनें।',
  'cmmn.instance.tabs': 'केस इंस्टेंस का डेटा',
  'cmmn.instance.tab.items': 'प्लान आइटम',
  'cmmn.instance.tab.history': 'इतिहास',
  'cmmn.instance.complete': 'केस पूरा करें',
  'cmmn.instance.terminate': 'केस समाप्त करें',
  'cmmn.instance.close': 'केस बंद करें',
  'cmmn.instance.completeTitle': 'केस इंस्टेंस पूरा करें',
  'cmmn.instance.completeText':
    '“{name}” का इंस्टेंस पूरा किया जाता है। यह तभी संभव है जब कोई प्लान आइटम सक्रिय न हो, और इसे पूर्ववत नहीं किया जा सकता।',
  'cmmn.instance.terminateTitle': 'केस इंस्टेंस समाप्त करें',
  'cmmn.instance.terminateText':
    '“{name}” का इंस्टेंस और उसके सभी अधूरे प्लान आइटम समाप्त किए जाते हैं; खुले टास्क हटा दिए जाते हैं। इसे पूर्ववत नहीं किया जा सकता।',
  'cmmn.instance.closeTitle': 'केस इंस्टेंस बंद करें',
  'cmmn.instance.closeText':
    '“{name}” का इंस्टेंस बंद किया जाता है: यह चल रहे इंस्टेंस से हट जाता है और केवल इतिहास में रहता है। इसे पूर्ववत नहीं किया जा सकता।',
  'cmmn.instance.done.complete': 'केस इंस्टेंस पूरा हुआ',
  'cmmn.instance.done.terminate': 'केस इंस्टेंस समाप्त हुआ',
  'cmmn.instance.done.close': 'केस इंस्टेंस बंद हुआ',
  'cmmn.instance.failed': 'केस इंस्टेंस बदला नहीं जा सका',

  'cmmn.state.active': 'सक्रिय',
  'cmmn.state.enabled': 'शुरू होने को तैयार',
  'cmmn.state.disabled': 'अक्षम',
  'cmmn.state.available': 'शर्त की प्रतीक्षा में',
  'cmmn.state.completed': 'पूरा',
  'cmmn.state.terminated': 'समाप्त',
  'cmmn.state.closed': 'बंद',

  'cmmn.type.casePlanModel': 'केस प्लान मॉडल',
  'cmmn.type.stage': 'चरण',
  'cmmn.type.humanTask': 'मानव टास्क',
  'cmmn.type.processTask': 'प्रोसेस टास्क',
  'cmmn.type.caseTask': 'केस टास्क',
  'cmmn.type.decisionTask': 'निर्णय टास्क',
  'cmmn.type.task': 'टास्क',
  'cmmn.type.milestone': 'माइलस्टोन',

  'cmmn.action.manualStart': 'शुरू करें',
  'cmmn.action.complete': 'पूरा करें',
  'cmmn.action.disable': 'अक्षम करें',
  'cmmn.action.reenable': 'फिर से सक्षम करें',
  'cmmn.action.terminate': 'समाप्त करें',
  'cmmn.action.of': '{action}: {name}',

  'cmmn.item.name': 'प्लान आइटम',
  'cmmn.item.type': 'प्रकार',
  'cmmn.item.required': 'आवश्यक',
  'cmmn.item.failed': '“{name}” बदला नहीं जा सका',
  'cmmn.item.done.manualStart': '“{name}” शुरू हुआ',
  'cmmn.item.done.complete': '“{name}” पूरा हुआ',
  'cmmn.item.done.disable': '“{name}” अक्षम हुआ',
  'cmmn.item.done.reenable': '“{name}” फिर से सक्षम हुआ',
  'cmmn.item.done.terminate': '“{name}” समाप्त हुआ',
  'cmmn.item.completeTitle': 'प्लान आइटम पूरा करें',
  'cmmn.item.completeText':
    '“{name}” बिना फ़ॉर्म के पूरा किया जाता है; उसका खुला टास्क भी पूरा हो जाता है। इसे पूर्ववत नहीं किया जा सकता।',
  'cmmn.item.terminateTitle': 'प्लान आइटम समाप्त करें',
  'cmmn.item.terminateText':
    '“{name}” और उसके भीतर सब कुछ समाप्त किया जाता है; उसके खुले टास्क हटा दिए जाते हैं। इसे पूर्ववत नहीं किया जा सकता।',
  'cmmn.items.empty': 'कोई प्लान आइटम खुला नहीं है',
  'cmmn.items.closed': 'बंद केस का कोई खुला प्लान आइटम नहीं होता। इतिहास दिखाता है कि क्या हुआ।',
  'cmmn.tasks.empty': 'इस केस इंस्टेंस का कोई खुला टास्क नहीं है',

  'cmmn.history.title': 'केस इतिहास',
  'cmmn.history.count': '{count} केस इंस्टेंस',
  'cmmn.history.count.one': '{count} केस इंस्टेंस',
  'cmmn.history.search': 'बिज़नेस कुंजी से खोजें',
  'cmmn.history.case': 'केस',
  'cmmn.history.filter.all': 'सभी',
  'cmmn.history.filter.open': 'बंद नहीं',
  'cmmn.history.filter.closed': 'बंद',
  'cmmn.history.empty': 'इतिहास में अभी कोई केस इंस्टेंस नहीं है',
  'cmmn.history.noMatch': 'कोई केस इंस्टेंस मेल नहीं खाता',
  'cmmn.history.itemsEmpty': 'इतिहास में इस केस इंस्टेंस का कोई प्लान आइटम नहीं है',
  'cmmn.variables.fromHistory': 'मान वैसे ही जैसे इतिहास ने सहेजे; बंद केस को बदला नहीं जा सकता।',

  'cmmn.legend': 'आरेख पर स्थितियाँ',
  'cmmn.diagram.label': 'केस आरेख',
  'cmmn.diagram.noLayout': 'इस मॉडल में आरेख लेआउट नहीं है, इसलिए केवल इसके प्लान आइटम सूचीबद्ध हैं।',
  'cmmn.task.diagramLabel': 'केस आरेख, टास्क का प्लान आइटम हाइलाइट है',
  'cmmn.task.openCase': 'केस को Cockpit में खोलें',

  'cmmn.deployments.openCase': 'केस “{name}” खोलें (संस्करण {version})',
  'palette.kind.case': 'केस इंस्टेंस',
};

export default messages;
