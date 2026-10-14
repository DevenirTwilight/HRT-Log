"""NON_PRODUCTION graph validation model, NOT a backup serializer or SQL restore.
All records/cipher commitments are synthetic; authentication is a prerequisite, not simulated crypto.
"""
from dataclasses import dataclass
from uuid import uuid4

@dataclass(frozen=True)
class Version:
    vault:str
    version:str
    commitment:tuple
    state:str='VERIFIED'

@dataclass(frozen=True)
class Link:
    record_uuid:str
    document:str
    state:str='ACTIVE'

class Graph:
    def __init__(self,records,documents,versions,links,epoch):
        self.records=records;self.documents=documents;self.versions=versions;self.links=links;self.epoch=epoch
    def validate(self,prior=()):
        seen={(v.vault,v.version):v.commitment for v in prior}
        for v in self.versions:
            key=(v.vault,v.version)
            if key in seen and seen[key]!=v.commitment:raise ValueError('Crypto identity conflicts')
            seen[key]=v.commitment
        for link in self.links:
            if link.record_uuid not in self.records or link.document not in self.documents:raise ValueError('Missing graph node')
            state,epoch=self.records[link.record_uuid]
            if link.state=='ACTIVE' and (state!='ACTIVE' or epoch!=self.epoch):raise ValueError('Invalid current target')
            if link.state not in ('ACTIVE','QUARANTINED','RETRACTED'):raise ValueError('Unknown link state')
        return True
    def old_record_restore(self):
        # No integer IDs are used to turn historical links ACTIVE.
        return str(uuid4()),[Link(x.record_uuid,x.document,'QUARANTINED') for x in self.links]
