create table simulation_run (
    id                      uuid primary key,
    status                  varchar(16)      not null,
    created_at              timestamptz      not null,
    started_at              timestamptz,
    finished_at             timestamptz,
    error_message           text,

    -- what was asked for
    input_file              varchar(512)     not null,
    start_time              varchar(32),
    end_time                varchar(32),
    max_messages            bigint,
    a_latency_nanos         bigint           not null,
    a_jitter_nanos          bigint           not null,
    a_loss                  double precision not null,
    a_bandwidth_bps         bigint           not null,
    a_max_queue_nanos       bigint           not null,
    b_latency_nanos         bigint           not null,
    b_jitter_nanos          bigint           not null,
    b_loss                  double precision not null,
    gap_timeout_nanos       bigint           not null,
    seed                    bigint           not null,

    -- what came out; null until the run completes
    messages                bigint,
    data_packets            bigint,
    duration_nanos          bigint,
    gaps                    bigint,
    messages_lost           bigint,
    average_bps             double precision,
    peak_bps                double precision,
    burst_windows_over      bigint,
    burst_windows_active    bigint,
    max_buffered_packets    integer
);

create index simulation_run_created_at on simulation_run (created_at desc);

create table run_fade (
    run_id              uuid             not null references simulation_run (id) on delete cascade,
    position            integer          not null,
    start_offset_nanos  bigint           not null,
    end_offset_nanos    bigint           not null,
    loss                double precision not null,
    primary key (run_id, position)
);

-- One row per (segment, source): latency percentiles and loss for a line alone or for the arbitrated stream.
create table latency_summary (
    run_id          uuid        not null references simulation_run (id) on delete cascade,
    segment         varchar(8)  not null,
    source          varchar(16) not null,
    messages_sent   bigint      not null,
    messages_lost   bigint      not null,
    messages_won    bigint,
    p50_nanos       bigint      not null,
    p99_nanos       bigint      not null,
    p999_nanos      bigint      not null,
    max_nanos       bigint      not null,
    primary key (run_id, segment, source)
);

create sequence timeline_second_seq increment by 50;

-- One row per second of exchange time, for charting a run.
create table timeline_second (
    id          bigint primary key,
    run_id      uuid   not null references simulation_run (id) on delete cascade,
    second      bigint not null,
    delivered   bigint not null,
    wins_a      bigint not null,
    wins_b      bigint not null,
    lost        bigint not null,
    p50_nanos   bigint not null,
    p99_nanos   bigint not null,
    unique (run_id, second)
);
