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
 * Response message for a trie state node from a specific blockchain database.
 *
 * @author Alexandra Roatis
 */
public final class ResponseTrieState extends Msg {

    private final TrieDatabase type;

    /** the trie node key */
    private final byte[] hash; // 32 bytes

    private final byte[] value; // TODO: min/max bytes

    public ResponseTrieState(final byte[] hash, final byte[] value, final TrieDatabase type) {
        super(Ver.V0, Ctrl.SYNC, Act.RESPONSE_TRIE_STATE);
        this.hash = hash;
        this.value = value;
        this.type = type;
    }

    public static ResponseTrieState decode(final byte[] message) {
        if (message == null) {
            return null;
        } else {
            RLPList list = (RLPList) RLP.decode2(message).get(0);
            if (list.size() != 3) {
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
                Value value = Value.fromRlpEncoded(list.get(2).getRLPData());
                if (!value.isBytes()) {
                    // TODO: estimate and check for min and max size
                    // incorrect message
                    return null;
                }
                return new ResponseTrieState(
                        hash.asBytes(), value.asBytes(), TrieDatabase.valueOf(type.asString()));
            }
        }
    }

    @Override
    public byte[] encode() {
        return RLP.encodeList(
                RLP.encodeString(type.toString()),
                RLP.encodeElement(hash),
                RLP.encodeElement(value));
    }
}
