/**
 * The extension's own persisted state: per-workspace rules and which ids it disabled.
 */

import { mkdir, readFile, rename, writeFile } from 'node:fs/promises';
import { dirname } from 'node:path';
import type { Detection, Plan } from '../types';

export interface WorkspaceState {
	lastDetection?: Pick<Detection, 'techs' | 'languages'>;
	lastPlan?: Plan;
	updatedAt?: string;
	/** "Never here" / "Enable all here": no prompts and nothing auto-disabled. */
	ignored?: boolean;
	include?: string[];
	exclude?: string[];
	/**
	 * Ids this extension put in the workspace's "Disable (Workspace)" list. Only these are ever removed
	 * again; extensions the user disabled there are left alone.
	 */
	disabledByUs?: string[];
	/** Last apply, to avoid reload loops when the result does not match the plan. */
	lastAttempt?: { hash: string; at: number };
}

export interface State {
	version: 1;
	workspaces: Record<string, WorkspaceState>;
}

function emptyState(): State {
	return { version: 1, workspaces: {} };
}

/** Decision history lives outside repositories, independently for each workspace. */
export class StateStore {
	constructor(readonly file: string) {}
	private pending: Promise<unknown> = Promise.resolve();

	async read(): Promise<State> {
		try {
			const state = JSON.parse(await readFile(this.file, 'utf8')) as State;
			return { ...emptyState(), ...state };
		} catch {
			return emptyState();
		}
	}

	update(mutate: (state: State) => void): Promise<State> {
		const next = this.pending.then(() => this.updateNow(mutate));
		this.pending = next.catch(() => {});
		return next;
	}

	private async updateNow(mutate: (state: State) => void): Promise<State> {
		const state = await this.read();
		mutate(state);
		await mkdir(dirname(this.file), { recursive: true });
		const tmp = `${this.file}.${process.pid}.tmp`;
		await writeFile(tmp, JSON.stringify(state, null, '\t'));
		await rename(tmp, this.file);
		return state;
	}

	async workspace(key: string): Promise<WorkspaceState> {
		return (await this.read()).workspaces[key] ?? {};
	}

	async updateWorkspace(key: string, mutate: (ws: WorkspaceState) => void): Promise<void> {
		await this.update(state => {
			const ws = state.workspaces[key] ?? {};
			mutate(ws);
			state.workspaces[key] = ws;
		});
	}
}
