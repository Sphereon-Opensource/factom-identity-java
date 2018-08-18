package org.blockchain_innovation.factom.client.data.conversion;

import org.blockchain_innovation.factom.client.data.FactomRuntimeException;
import org.blockchain_innovation.factom.client.data.model.response.factomd.EntryResponse;

import java.util.List;

public class EntryOperations {
    private ByteOperations byteOps = new ByteOperations();

    public String calculateChainId(EntryResponse entryResponse) {
        return calculateChainId(entryResponse.getExtIds());
    }

    public String calculateChainId(List<String> externalIds) {
        byte[] bytes = new byte[0];
        if (externalIds != null) {
            for (String externalId : externalIds) {
                bytes = byteOps.concat(bytes, Digests.SHA_256.digest(externalId));
            }
        }
        // TODO: 14-8-2018 Check empty/null list
        byte[] chainId = Digests.SHA_256.digest(bytes);
        return Encoding.HEX.encode(chainId);
    }

    public String calculateFirstEntryHash(List<String> externalIds, String content) {
        return calculateEntryHash(externalIds, content, null);
    }

    public String calculateEntryHash(List<String> externalIds, String content, String chainId) {
        byte[] entryBytes = entryToBytes(externalIds, content, chainId);
        byte[] bytes = byteOps.concat(Digests.SHA_512.digest(entryBytes), entryBytes);
        return Encoding.HEX.encode(Digests.SHA_256.digest(bytes));

    }

    public byte[] entryToBytes(List<String> externalIds, String content) {
        return entryToBytes(externalIds, content, null);
    }

    public byte[] entryToBytes(List<String> externalIds, String content, String chainId) {
        byte[] chainIdBytes;
        byte[] bytes = new byte[0];
        if (StringUtils.isNotEmpty(chainId)) {
            chainIdBytes = Encoding.HEX.decode(chainId);
        } else {
            chainIdBytes = Encoding.HEX.decode(calculateChainId(externalIds));
        }

        // Version 0
        bytes = byteOps.concat(bytes, (byte) 0);
        bytes = byteOps.concat(bytes, chainIdBytes);
        bytes = byteOps.concat(bytes, externalIdsToBytes(externalIds));
        if (StringUtils.isNotEmpty(content)) {
            bytes = byteOps.concat(bytes, Encoding.UTF_8.decode(content));
        }
        return bytes;

    }


    protected byte[] externalIdsToBytes(List<String> externalIds) {
        if (externalIds == null || externalIds.isEmpty()) {
            return new byte[]{(byte) 0};
        }
        byte[] bytes = new byte[0];
        short externalIdLength = 0;

        for (String externalId : externalIds) {
            if (externalId == null) {
                throw new FactomRuntimeException.AssertionException("External Id needs a value");
            }
            byte[] extIdAsBytes = Encoding.UTF_8.decode(externalId);
            int length = extIdAsBytes.length;
            bytes = byteOps.concat(bytes, byteOps.toShortBytes(length));
            bytes = byteOps.concat(bytes, extIdAsBytes);

            // We need to add 2 to store the next section's externalID length value
            externalIdLength += length + 2;
        }
        bytes = byteOps.concat(byteOps.toShortBytes(externalIdLength), bytes);
        return bytes;
    }
}