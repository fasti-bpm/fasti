/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { MessageKey } from '../i18n';

/**
 * The resource types of the engine that authorizations can be given for,
 * with the permissions each of them knows (see the enums `Resources` and
 * `*Permissions` of the engine). `ALL` and `NONE` exist for every type and
 * are offered separately.
 */
export interface ResourceType {
  id: number;
  label: MessageKey;
  permissions: readonly string[];
  /** typical resource ids, offered as suggestions */
  suggestions?: readonly string[];
  /** what the resource id is for this type */
  idHint: MessageKey;
}

const CRUD = ['READ', 'UPDATE', 'CREATE', 'DELETE'] as const;

/** The default type: who may open which application. */
export const APPLICATION: ResourceType = {
  id: 0,
  label: 'admin.resource.application',
  permissions: ['ACCESS'],
  suggestions: ['*', 'tasklist', 'cockpit', 'admin', 'welcome'],
  idHint: 'admin.resourceId.application',
};

export const RESOURCE_TYPES: readonly ResourceType[] = [
  APPLICATION,
  { id: 1, label: 'admin.resource.user', permissions: CRUD, idHint: 'admin.resourceId.user' },
  { id: 2, label: 'admin.resource.group', permissions: CRUD, idHint: 'admin.resourceId.group' },
  { id: 3, label: 'admin.resource.groupMembership', permissions: ['CREATE', 'DELETE'], idHint: 'admin.resourceId.group' },
  { id: 4, label: 'admin.resource.authorization', permissions: CRUD, idHint: 'admin.resourceId.authorization' },
  { id: 5, label: 'admin.resource.filter', permissions: CRUD, idHint: 'admin.resourceId.filter' },
  {
    id: 6,
    label: 'admin.resource.processDefinition',
    permissions: [
      'READ',
      'UPDATE',
      'DELETE',
      'SUSPEND',
      'CREATE_INSTANCE',
      'READ_INSTANCE',
      'UPDATE_INSTANCE',
      'RETRY_JOB',
      'SUSPEND_INSTANCE',
      'DELETE_INSTANCE',
      'MIGRATE_INSTANCE',
      'READ_INSTANCE_VARIABLE',
      'UPDATE_INSTANCE_VARIABLE',
      'READ_TASK',
      'UPDATE_TASK',
      'TASK_ASSIGN',
      'TASK_WORK',
      'READ_TASK_VARIABLE',
      'UPDATE_TASK_VARIABLE',
      'READ_HISTORY',
      'READ_HISTORY_VARIABLE',
      'UPDATE_HISTORY',
      'DELETE_HISTORY',
    ],
    idHint: 'admin.resourceId.processDefinition',
  },
  {
    id: 7,
    label: 'admin.resource.task',
    permissions: [...CRUD, 'TASK_ASSIGN', 'TASK_WORK', 'READ_VARIABLE', 'UPDATE_VARIABLE', 'READ_HISTORY'],
    idHint: 'admin.resourceId.task',
  },
  {
    id: 8,
    label: 'admin.resource.processInstance',
    permissions: [...CRUD, 'RETRY_JOB', 'SUSPEND', 'UPDATE_VARIABLE'],
    idHint: 'admin.resourceId.processInstance',
  },
  { id: 9, label: 'admin.resource.deployment', permissions: ['READ', 'CREATE', 'DELETE'], idHint: 'admin.resourceId.deployment' },
  {
    id: 10,
    label: 'admin.resource.decisionDefinition',
    permissions: ['READ', 'UPDATE', 'CREATE_INSTANCE', 'READ_HISTORY', 'DELETE_HISTORY'],
    idHint: 'admin.resourceId.decisionDefinition',
  },
  {
    id: 14,
    label: 'admin.resource.decisionRequirementsDefinition',
    permissions: ['READ'],
    idHint: 'admin.resourceId.decisionDefinition',
  },
  { id: 11, label: 'admin.resource.tenant', permissions: CRUD, idHint: 'admin.resourceId.tenant' },
  { id: 12, label: 'admin.resource.tenantMembership', permissions: ['CREATE', 'DELETE'], idHint: 'admin.resourceId.tenant' },
  {
    id: 13,
    label: 'admin.resource.batch',
    permissions: [
      ...CRUD,
      'READ_HISTORY',
      'DELETE_HISTORY',
      'CREATE_BATCH_MIGRATE_PROCESS_INSTANCES',
      'CREATE_BATCH_MODIFY_PROCESS_INSTANCES',
      'CREATE_BATCH_RESTART_PROCESS_INSTANCES',
      'CREATE_BATCH_DELETE_RUNNING_PROCESS_INSTANCES',
      'CREATE_BATCH_DELETE_FINISHED_PROCESS_INSTANCES',
      'CREATE_BATCH_DELETE_DECISION_INSTANCES',
      'CREATE_BATCH_SET_JOB_RETRIES',
      'CREATE_BATCH_SET_EXTERNAL_TASK_RETRIES',
      'CREATE_BATCH_UPDATE_PROCESS_INSTANCES_SUSPEND',
      'CREATE_BATCH_SET_REMOVAL_TIME',
      'CREATE_BATCH_SET_VARIABLES',
      'CREATE_BATCH_CORRELATE_MESSAGE',
    ],
    idHint: 'admin.resourceId.batch',
  },
  {
    id: 17,
    label: 'admin.resource.operationLogCategory',
    permissions: ['READ', 'UPDATE', 'DELETE'],
    suggestions: ['*', 'TaskWorker', 'Admin', 'Operator'],
    idHint: 'admin.resourceId.operationLogCategory',
  },
  { id: 19, label: 'admin.resource.historicTask', permissions: ['READ', 'READ_VARIABLE'], idHint: 'admin.resourceId.task' },
  {
    id: 20,
    label: 'admin.resource.historicProcessInstance',
    permissions: ['READ'],
    idHint: 'admin.resourceId.processInstance',
  },
  { id: 21, label: 'admin.resource.system', permissions: ['READ', 'SET', 'DELETE'], suggestions: ['*'], idHint: 'admin.resourceId.system' },
];

export function resourceType(id: number): ResourceType {
  return RESOURCE_TYPES.find((type) => type.id === id) ?? APPLICATION;
}

/** The permissions of an authorization as the user reads them. */
export function permissionSummary(permissions: readonly string[]): 'all' | 'none' | string[] {
  if (permissions.includes('ALL')) {
    return 'all';
  }
  const named = permissions.filter((permission) => permission !== 'NONE');
  return named.length === 0 ? 'none' : named;
}
