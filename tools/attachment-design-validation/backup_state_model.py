"""Deterministic durability/restore design model; no encryption, archive or disk implementation.
AUTHENTICATED is an input assumption, not something this model cryptographically proves.
"""
from dataclasses import dataclass, field

class Rejected(ValueError): pass
@dataclass(frozen=True)
class Package:
    archive: str
    digest: str
    objects: tuple[str, ...]
    links: tuple[tuple[str, str], ...]
    records: tuple[str, ...]
    authenticated: bool = True
    final_manifest: bool = True
    exact_eof: bool = True
    bytes_needed: int = 4096

@dataclass
class Store:
    revision: int = 1
    active: str = 'old'
    durable_files: dict[str, tuple[str, ...]] = field(default_factory=lambda: {'old': ('old-object',)})
    volatile_files: dict[str, tuple[str, ...]] = field(default_factory=dict)
    records: tuple[str, ...] = ('old-record',)
    links: tuple[tuple[str, str], ...] = (('old-record','old-object'),)
    epoch: str = 'old-epoch'
    journal: dict = field(default_factory=dict)
    receipts: dict = field(default_factory=dict)
    leases: set = field(default_factory=set)

    def crash(self):
        self.volatile_files.clear()
        # Durable DB/pointer/journal survives by model assumption. Real fsync/SQLCipher NOT proved.

    def restore(self, package, operation, expected, free=1<<30, fail=None):
        if operation in self.receipts:
            receipt=self.receipts[operation]
            if receipt[:2] != (package.archive, package.digest): raise Rejected('idempotency collision')
            if self.revision != receipt[2]: raise Rejected('dataset changed since completed restore')
            return self.active
        if expected != self.revision: raise Rejected('stale revision')
        if not (package.authenticated and package.final_manifest and package.exact_eof): raise Rejected('unauthenticated/incomplete package')
        if len(set(package.objects))!=len(package.objects) or len(set(package.records))!=len(package.records): raise Rejected('duplicate identity')
        if len(set(package.links))!=len(package.links): raise Rejected('duplicate link')
        if any(r not in package.records or o not in package.objects for r,o in package.links): raise Rejected('dangling graph')
        if package.bytes_needed > free: raise Rejected('space reservation')
        generation='new-'+operation
        prior=self.journal.get(operation)
        if prior and prior['digest'] != package.digest: raise Rejected('pending idempotency collision')
        self.journal[operation]={'generation':generation,'digest':package.digest,'phase':'PREPARED'}
        def boundary(name):
            if fail == name:
                self.crash(); raise Rejected('injected '+name)
        boundary('reserved')
        self.volatile_files[generation]=package.objects
        self.journal[operation]['phase']='WRITING'; boundary('writing')
        self.journal[operation]['phase']='AUTHENTICATED'; boundary('authenticated')
        # Simulated file sync, no-replace publication and directory sync form durable generation.
        boundary('file_synced'); boundary('published_before_dirsync')
        self.durable_files[generation]=self.volatile_files.pop(generation)
        boundary('directory_synced')
        if self.revision != expected: raise Rejected('CAS failed')
        # One atomic SQL commit in this model; NOT several independent assignment durability claims.
        self.active=generation; self.records=package.records; self.links=package.links
        self.revision+=1; self.epoch='archive-epoch-'+package.archive
        self.receipts[operation]=(package.archive,package.digest,self.revision)
        self.journal[operation]['phase']='COMMITTED'
        boundary('committed_ack_lost')
        return self.active

    def reconcile(self):
        for row in self.journal.values():
            g=row['generation']
            if row['phase']=='COMMITTED':
                if g not in self.durable_files: raise Rejected('committed file missing: block access, never fabricate')
            elif g != self.active and g not in self.leases:
                self.durable_files.pop(g,None); row['phase']='ABORTED'
        self.volatile_files.clear()

    def old_record_only_restore(self, records, epoch, fail=False):
        if epoch == self.epoch: raise Rejected('must rotate recordEpoch')
        if fail: raise Rejected('all old rows/epoch/links must roll back together')
        self.records=records; self.links=(); self.epoch=epoch; self.revision+=1
        # Objects and journal remain; old associations are quarantined by actual repository adapter.

    def collect(self, generation):
        if generation == self.active or generation in self.leases: raise Rejected('active or leased')
        if any(r['generation']==generation and r['phase'] not in ('ABORTED','COMMITTED') for r in self.journal.values()): raise Rejected('pending ownership')
        self.durable_files.pop(generation,None)

def backup_success(*, manifest, eof, close_ok, canceled, durable_receipt):
    return manifest and eof and close_ok and not canceled and durable_receipt
