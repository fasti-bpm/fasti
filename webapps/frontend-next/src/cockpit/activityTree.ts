/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useCallback, useRef } from 'react';
import type { ActivityInstance } from '../api/cockpit';

/** An activity instance as the tree of the instance page shows it. */
export interface TreeNode {
  id: string;
  activityId: string;
  name: string;
  /** a token that is about to enter or has just left the activity */
  transition: boolean;
  incidents: number;
  children: TreeNode[];
}

interface Built {
  names: Map<string, string>;
  transition: boolean;
  node: TreeNode;
}

/**
 * The node built for an answer of the server. A refresh that brings the same
 * tree hands back the same objects (the query keeps the parts of an answer
 * that did not change), so a part of the tree that did not change keeps its
 * node as well and is not rendered again.
 */
const built = new WeakMap<ActivityInstance, Built>();

export function toTree(instance: ActivityInstance, names: Map<string, string>, transition = false): TreeNode {
  const known = built.get(instance);
  if (known && known.names === names && known.transition === transition) {
    return known.node;
  }
  const node: TreeNode = {
    id: instance.id,
    activityId: instance.activityId,
    name: instance.activityName ?? names.get(instance.activityId) ?? instance.activityId,
    transition,
    incidents: instance.incidents?.length ?? 0,
    children: [
      ...(instance.childActivityInstances ?? []).map((child) => toTree(child, names)),
      ...(instance.childTransitionInstances ?? []).map((child) => toTree(child, names, true)),
    ],
  };
  built.set(instance, { names, transition, node });
  return node;
}

export function walk(node: TreeNode, visit: (node: TreeNode, root: boolean) => void, root = true): void {
  visit(node, root);
  node.children.forEach((child) => walk(child, visit, false));
}

/** How many activity and transition instances a tree has. */
export function treeSize(instance: ActivityInstance | undefined): number {
  if (!instance) return 0;
  let size = 1;
  for (const child of instance.childActivityInstances ?? []) size += treeSize(child);
  for (const child of instance.childTransitionInstances ?? []) size += treeSize(child);
  return size;
}

const QUIET = 2 * 60_000;
const LONG_QUIET = 10 * 60_000;
const SLOWEST = 5 * 60_000;

/**
 * How long to wait before the tree of an instance is read again. For every
 * answer the server loads all executions and incidents of the instance and
 * builds the tree anew, so the cost grows with the instance: a large tree
 * (a multi-instance body with hundreds of elements) is read less often, and
 * so is one that has not changed for a while. A change, also one the user
 * made, brings the usual interval back.
 */
export function treeInterval(base: number | false, size: number, unchangedFor: number): number | false {
  if (base === false) return false;
  const bySize = size > 500 ? 4 : size > 100 ? 2 : 1;
  const byQuiet = unchangedFor >= LONG_QUIET ? 4 : unchangedFor >= QUIET ? 2 : 1;
  // never faster than asked for, e.g. while nobody is at the screen
  return Math.max(base, Math.min(base * Math.max(bySize, byQuiet), SLOWEST));
}

/**
 * The `refetchInterval` of the query of the tree: a function of its latest answer, which the
 * query calls after every read.
 */
export function useTreeInterval(base: number | false): (tree: ActivityInstance | undefined) => number | false {
  const seen = useRef<{ tree: ActivityInstance | undefined; since: number; size: number } | null>(null);
  return useCallback(
    (tree) => {
      // an answer equal to the one before is the same object, so this is when the tree last changed
      if (!seen.current || seen.current.tree !== tree) {
        seen.current = { tree, since: Date.now(), size: treeSize(tree) };
      }
      return treeInterval(base, seen.current.size, Date.now() - seen.current.since);
    },
    [base],
  );
}
