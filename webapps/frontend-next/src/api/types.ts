/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
export interface AuthInfo {
  userId: string;
  authorizedApps: string[];
}

export interface UserProfile {
  id: string;
  firstName: string | null;
  lastName: string | null;
  email: string | null;
}

export interface EngineInfo {
  name: string;
}

export interface FilterVariable {
  name: string;
  label?: string;
}

export interface FilterProperties {
  priority?: number;
  description?: string;
  color?: string;
  refresh?: boolean;
  showUndefinedVariable?: boolean;
  variables?: FilterVariable[];
}

export interface TaskFilter {
  id: string;
  name: string;
  owner: string | null;
  resourceType: string;
  properties: FilterProperties | null;
  /** missing if the server could not count the tasks of the filter */
  itemCount?: number | null;
}

export interface Task {
  id: string;
  name: string | null;
  assignee: string | null;
  owner: string | null;
  created: string;
  due: string | null;
  followUp: string | null;
  lastUpdated?: string | null;
  delegationState: string | null;
  description: string | null;
  executionId: string | null;
  parentTaskId: string | null;
  priority: number;
  processDefinitionId: string | null;
  processInstanceId: string | null;
  caseDefinitionId: string | null;
  caseInstanceId: string | null;
  caseExecutionId: string | null;
  taskDefinitionKey: string | null;
  suspended?: boolean;
  formKey: string | null;
  camundaFormRef?: unknown;
  tenantId: string | null;
}

export interface VariableValue {
  value: unknown;
  type?: string | null;
  valueInfo?: Record<string, unknown> | null;
}

export type VariableMap = Record<string, VariableValue>;

export interface TaskVariable extends VariableValue {
  name: string;
}

/** A task of a filter result together with the variables the filter asks for. */
export interface TaskListItem extends Task {
  variables: TaskVariable[];
  assigneeName?: string;
  processDefinitionName?: string;
  /** the key of the process; definition ids do not always contain it */
  processDefinitionKey?: string;
  /** the business key of the instance: what the case is called in the business */
  businessKey?: string | null;
  /** the absent user the task is held for by a substitute */
  absentAssignee?: string;
}

export interface TaskPage {
  count: number;
  tasks: TaskListItem[];
}

export type SortOrder = 'asc' | 'desc';

export type TaskSortBy =
  | 'created'
  | 'priority'
  | 'dueDate'
  | 'followUpDate'
  | 'nameCaseInsensitive'
  | 'assignee'
  | 'processVariable'
  | 'taskVariable';

export interface TaskSorting {
  sortBy: TaskSortBy;
  sortOrder: SortOrder;
  /** for a sort by a variable: its name and the type its values are stored with */
  parameters?: { variable: string; type: string };
}

export interface IdentityLink {
  userId: string | null;
  groupId: string | null;
  type: string;
}

export interface TaskComment {
  id: string;
  userId: string | null;
  time: string;
  message: string;
  taskId: string;
}

export interface UserOperation {
  id: string;
  userId: string | null;
  timestamp: string;
  operationId: string;
  operationType: string;
  entityType: string;
  property: string | null;
  orgValue: string | null;
  newValue: string | null;
}

export interface FormInfo {
  key: string | null;
  contextPath: string | null;
  camundaFormRef?: { key: string; binding: string; version?: number | null } | null;
}

export interface ProcessDefinition {
  id: string;
  key: string;
  name: string | null;
  description: string | null;
  version: number;
  versionTag: string | null;
  tenantId: string | null;
  startableInTasklist?: boolean;
}

export interface ProcessInstanceRef {
  id: string;
  definitionId: string;
  businessKey: string | null;
}

/** What a form is opened for: a task to work on or a process to start. */
export type FormTarget =
  | { type: 'task'; taskId: string }
  | { type: 'start'; processDefinitionId: string };
