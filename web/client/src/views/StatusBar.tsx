/*
 * Copyright 2026 Maxime Ethier - Consultant en Bioinformatique/Biocomputing Consultant
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { useStore } from '../controllers/useStore';
import { useMessages } from '../i18n';
import type { AppModel } from '../model/AppModel';

/**
 * Status bar at the bottom of the window: a spinner while a request runs, and the last message,
 * in red for an error. The message is translated again when the language changes.
 */
export function StatusBar({ model }: { model: AppModel }) {
  const m = useMessages();
  const status = useStore(model.status);
  const busy = useStore(model.busy);
  return (
    <div className="status-bar">
      <span className={busy ? 'spinner' : 'spinner idle'} aria-hidden="true" />
      <span className={status.error ? 'status-text error' : 'status-text'} role="status">
        {status.render(m)}
      </span>
    </div>
  );
}
