/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */

package org.aion.zero.impl.sync.handler;

import org.aion.base.util.ByteArrayWrapper;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Handler;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.Ver;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.sync.TrieDatabase;
import org.aion.zero.impl.sync.msg.RequestTrieState;
import org.aion.zero.impl.sync.msg.ResponseTrieState;
import org.slf4j.Logger;

/**
 * Handler for trie state requests from the network.
 *
 * @author Alexandra Roatis
 */
public final class RequestTrieStateHandler extends Handler {

    private final Logger log;

    private final IAionBlockchain chain;

    private final IP2pMgr mgr;

    public RequestTrieStateHandler(
            final Logger _log, final IAionBlockchain _chain, final IP2pMgr _mgr) {
        super(Ver.V1, Ctrl.SYNC, Act.REQUEST_TRIE_STATE);
        this.log = _log;
        this.chain = _chain;
        this.mgr = _mgr;
    }

    @Override
    public void receive(int _nodeIdHashcode, String _displayId, final byte[] _msgBytes) {

        RequestTrieState request = RequestTrieState.decode(_msgBytes);

        if (request != null) {
            TrieDatabase db = request.getType();
            byte[] key = request.getHash();

            if (log.isDebugEnabled()) {
                this.log.debug(
                        "<req-trie from-db={} hash={} node={}>",
                        db,
                        ByteArrayWrapper.wrap(key),
                        _displayId);
            }

            // TODO: retrieve from blockchain depending on db
            byte[] value = null;
            switch (db) {
                case STATE:
                    break;
                case DETAILS:
                    break;
                case STORAGE:
                    break;
            }

            this.mgr.send(_nodeIdHashcode, _displayId, new ResponseTrieState(key, value, db));
        } else {
            this.log.error(
                    "<req-trie decode-error msg-bytes={} node={}>",
                    _msgBytes == null ? 0 : _msgBytes.length,
                    _nodeIdHashcode);
        }
    }
}
