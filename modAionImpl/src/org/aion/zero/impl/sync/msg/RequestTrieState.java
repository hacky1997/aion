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

package org.aion.zero.impl.sync.msg;

import org.aion.p2p.Ctrl;
import org.aion.p2p.Msg;
import org.aion.p2p.Ver;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPList;
import org.aion.rlp.Value;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.sync.TrieDatabase;

/**
 * Request message for a trie state from a specific blockchain database.
 *
 * @author Alexandra Roatis
 */
public final class RequestTrieState extends Msg {
    private final TrieDatabase type;
    private final byte[] hash;

    /**
     * @param hash the hash key of the requested trie node
     * @param type the blockchain database in which the key should be found
     */
    public RequestTrieState(byte[] hash, TrieDatabase type) {
        super(Ver.V1, Ctrl.SYNC, Act.REQUEST_TRIE_STATE);
        this.hash = hash;
        this.type = type;
    }

    public static RequestTrieState decode(final byte[] message) {
        if (message == null) {
            return null;
        } else {
            RLPList list = (RLPList) RLP.decode2(message).get(0);
            if (list.size() != 2) {
                // incorrect message
                return null;
            } else {
                Value type = Value.fromRlpEncoded(list.get(0).getRLPData());
                if (!type.isString()) {
                    // incorrect message
                    return null;
                }
                Value hash = Value.fromRlpEncoded(list.get(1).getRLPData());
                if (!hash.isBytes() || hash.asBytes().length != 32) {
                    // incorrect message
                    return null;
                }
                return new RequestTrieState(hash.asBytes(), TrieDatabase.valueOf(type.asString()));
            }
        }
    }

    @Override
    public byte[] encode() {
        return RLP.encodeList(RLP.encodeString(type.toString()), RLP.encodeElement(hash));
    }

    public TrieDatabase getType() {
        return type;
    }

    public byte[] getHash() {
        return hash;
    }
}
