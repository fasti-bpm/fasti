/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { Catalog } from '../../catalog';

/** Indonesian messages of the pages of cases (CMMN). */
const messages: Catalog = {
  'cmmn.nav': 'Kasus (CMMN)',
  'cmmn.nav.history': 'Riwayat kasus',

  'cmmn.definitions.title': 'Definisi kasus',
  'cmmn.definitions.count': '{count} definisi kasus',
  'cmmn.definitions.search': 'Cari definisi kasus',
  'cmmn.definitions.noMatch': 'Tidak ada definisi kasus yang cocok dengan pencarian',
  'cmmn.definitions.empty': 'Belum ada definisi kasus yang di-deploy',
  'cmmn.definitions.emptyHint': 'Kasus dimodelkan dalam CMMN dan di-deploy sebagai berkas .cmmn, misalnya di halaman',
  'cmmn.definitions.open': 'Instans terbuka',

  'cmmn.definition.title': 'Definisi kasus',
  'cmmn.definition.notFound': 'Definisi kasus ini tidak ada',
  'cmmn.definition.instances': 'Instans terbuka dari versi ini',
  'cmmn.definition.instancesEmpty': 'Versi ini tidak memiliki instans terbuka',
  'cmmn.definition.businessKeySearch': 'Kunci bisnis (persis)',
  'cmmn.definition.noMatch': 'Tidak ada instans terbuka dengan kunci bisnis ini',
  'cmmn.definition.diagramLabel': 'Diagram kasus dengan jumlah item rencana yang terbuka',
  'cmmn.definition.diagramHint': 'Angka menunjukkan berapa item rencana dari instans terbuka yang aktif atau menunggu dimulai.',
  'cmmn.definition.truncated': 'Angka pada diagram hanya menghitung {count} item rencana pertama.',

  'cmmn.create.action': 'Buat instans kasus',
  'cmmn.create.title': 'Buat instans kasus',
  'cmmn.create.text': 'Membuat instans “{name}”, versi {version}. Item rencananya langsung dimulai.',
  'cmmn.create.done': 'Instans kasus dibuat',
  'cmmn.create.failed': 'Instans kasus tidak dapat dibuat',

  'cmmn.instance': 'Instans kasus',
  'cmmn.instance.gone': 'Instans kasus ini tidak ada',
  'cmmn.instance.goneHint': 'ID mungkin salah, atau instans sudah ditutup dan riwayatnya dihapus.',
  'cmmn.instance.closedNote': 'Instans kasus ini sudah ditutup. Ditampilkan sebagaimana disimpan oleh riwayat dan tidak dapat diubah.',
  'cmmn.instance.created': 'Dibuat',
  'cmmn.instance.closedAt': 'Ditutup',
  'cmmn.instance.backToDefinition': 'Kembali ke definisi kasus',
  'cmmn.instance.parentProcess': 'Dimulai oleh instans proses',
  'cmmn.instance.parentCase': 'Dimulai oleh instans kasus',
  'cmmn.instance.diagramLabel': 'Diagram kasus dengan status setiap item rencana',
  'cmmn.instance.diagramHint': 'Pilih item rencana di diagram untuk hanya melihat item itu di daftar item rencana.',
  'cmmn.instance.tabs': 'Data instans kasus',
  'cmmn.instance.tab.items': 'Item rencana',
  'cmmn.instance.tab.history': 'Riwayat',
  'cmmn.instance.complete': 'Selesaikan kasus',
  'cmmn.instance.terminate': 'Hentikan kasus',
  'cmmn.instance.close': 'Tutup kasus',
  'cmmn.instance.completeTitle': 'Selesaikan instans kasus',
  'cmmn.instance.completeText':
    'Instans “{name}” diselesaikan. Ini hanya bisa jika tidak ada item rencana yang masih aktif, dan tidak dapat dibatalkan.',
  'cmmn.instance.terminateTitle': 'Hentikan instans kasus',
  'cmmn.instance.terminateText':
    'Instans “{name}” dan semua item rencananya yang belum selesai dihentikan; tugas terbuka dihapus. Tidak dapat dibatalkan.',
  'cmmn.instance.closeTitle': 'Tutup instans kasus',
  'cmmn.instance.closeText':
    'Instans “{name}” ditutup: keluar dari instans yang berjalan dan hanya disimpan oleh riwayat. Tidak dapat dibatalkan.',
  'cmmn.instance.done.complete': 'Instans kasus diselesaikan',
  'cmmn.instance.done.terminate': 'Instans kasus dihentikan',
  'cmmn.instance.done.close': 'Instans kasus ditutup',
  'cmmn.instance.failed': 'Instans kasus tidak dapat diubah',

  'cmmn.state.active': 'Aktif',
  'cmmn.state.enabled': 'Siap dimulai',
  'cmmn.state.disabled': 'Dinonaktifkan',
  'cmmn.state.available': 'Menunggu kondisi',
  'cmmn.state.completed': 'Selesai',
  'cmmn.state.terminated': 'Dihentikan',
  'cmmn.state.closed': 'Ditutup',

  'cmmn.type.casePlanModel': 'Model rencana kasus',
  'cmmn.type.stage': 'Tahap',
  'cmmn.type.humanTask': 'Tugas manusia',
  'cmmn.type.processTask': 'Tugas proses',
  'cmmn.type.caseTask': 'Tugas kasus',
  'cmmn.type.decisionTask': 'Tugas keputusan',
  'cmmn.type.task': 'Tugas',
  'cmmn.type.milestone': 'Tonggak',

  'cmmn.action.manualStart': 'Mulai',
  'cmmn.action.complete': 'Selesaikan',
  'cmmn.action.disable': 'Nonaktifkan',
  'cmmn.action.reenable': 'Aktifkan lagi',
  'cmmn.action.terminate': 'Hentikan',
  'cmmn.action.of': '{action}: {name}',

  'cmmn.item.name': 'Item rencana',
  'cmmn.item.type': 'Jenis',
  'cmmn.item.required': 'Wajib',
  'cmmn.item.failed': '“{name}” tidak dapat diubah',
  'cmmn.item.done.manualStart': '“{name}” dimulai',
  'cmmn.item.done.complete': '“{name}” diselesaikan',
  'cmmn.item.done.disable': '“{name}” dinonaktifkan',
  'cmmn.item.done.reenable': '“{name}” diaktifkan lagi',
  'cmmn.item.done.terminate': '“{name}” dihentikan',
  'cmmn.item.completeTitle': 'Selesaikan item rencana',
  'cmmn.item.completeText':
    '“{name}” diselesaikan tanpa formulir; tugas terbukanya juga diselesaikan. Tidak dapat dibatalkan.',
  'cmmn.item.terminateTitle': 'Hentikan item rencana',
  'cmmn.item.terminateText':
    '“{name}” beserta semua isinya dihentikan; tugas terbukanya dihapus. Tidak dapat dibatalkan.',
  'cmmn.items.empty': 'Tidak ada item rencana yang terbuka',
  'cmmn.items.closed': 'Kasus yang ditutup tidak memiliki item rencana terbuka. Riwayat menunjukkan apa yang terjadi.',
  'cmmn.tasks.empty': 'Instans kasus ini tidak memiliki tugas terbuka',

  'cmmn.history.title': 'Riwayat kasus',
  'cmmn.history.count': '{count} instans kasus',
  'cmmn.history.search': 'Cari menurut kunci bisnis',
  'cmmn.history.case': 'Kasus',
  'cmmn.history.filter.all': 'Semua',
  'cmmn.history.filter.open': 'Belum ditutup',
  'cmmn.history.filter.closed': 'Ditutup',
  'cmmn.history.empty': 'Riwayat belum memiliki instans kasus',
  'cmmn.history.noMatch': 'Tidak ada instans kasus yang cocok',
  'cmmn.history.itemsEmpty': 'Riwayat tidak memiliki item rencana dari instans kasus ini',
  'cmmn.variables.fromHistory': 'Nilai sebagaimana disimpan oleh riwayat; kasus yang ditutup tidak dapat diubah.',

  'cmmn.legend': 'Status pada diagram',
  'cmmn.diagram.label': 'Diagram kasus',
  'cmmn.diagram.noLayout': 'Model ini tidak memiliki tata letak diagram, jadi hanya item rencananya yang dicantumkan.',
  'cmmn.task.diagramLabel': 'Diagram kasus, item rencana dari tugas disorot',
  'cmmn.task.openCase': 'Buka kasus di Cockpit',

  'palette.kind.case': 'Instans kasus',
};

export default messages;
