--
-- PostgreSQL database dump
--

\restrict O4ZUzBG7WHVbmJvQ4MXc3kZz8MHbo9jFvmmcQRD0HyWfzRoTLNqzmDQqhUYd5Bj

-- Dumped from database version 16.11
-- Dumped by pg_dump version 17.6

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: monthly_stats; Type: TABLE; Schema: settlement; Owner: app_user
--

CREATE TABLE settlement.monthly_stats (
    id bigint NOT NULL,
    stat_month character(7) NOT NULL,
    total_gross_amount numeric(20,4) DEFAULT 0 NOT NULL,
    total_fee_amount numeric(20,4) DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE settlement.monthly_stats OWNER TO app_user;

--
-- Name: monthly_stats_id_seq; Type: SEQUENCE; Schema: settlement; Owner: app_user
--

CREATE SEQUENCE settlement.monthly_stats_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE settlement.monthly_stats_id_seq OWNER TO app_user;

--
-- Name: monthly_stats_id_seq; Type: SEQUENCE OWNED BY; Schema: settlement; Owner: app_user
--

ALTER SEQUENCE settlement.monthly_stats_id_seq OWNED BY settlement.monthly_stats.id;


--
-- Name: monthly_stats id; Type: DEFAULT; Schema: settlement; Owner: app_user
--

ALTER TABLE ONLY settlement.monthly_stats ALTER COLUMN id SET DEFAULT nextval('settlement.monthly_stats_id_seq'::regclass);


--
-- Name: monthly_stats monthly_stats_pkey; Type: CONSTRAINT; Schema: settlement; Owner: app_user
--

ALTER TABLE ONLY settlement.monthly_stats
    ADD CONSTRAINT monthly_stats_pkey PRIMARY KEY (id);


--
-- Name: monthly_stats uq_monthly_stats_month; Type: CONSTRAINT; Schema: settlement; Owner: app_user
--

ALTER TABLE ONLY settlement.monthly_stats
    ADD CONSTRAINT uq_monthly_stats_month UNIQUE (stat_month);


--
-- Name: idx_monthly_stats_month; Type: INDEX; Schema: settlement; Owner: app_user
--

CREATE INDEX idx_monthly_stats_month ON settlement.monthly_stats USING btree (stat_month);


--
-- PostgreSQL database dump complete
--

\unrestrict O4ZUzBG7WHVbmJvQ4MXc3kZz8MHbo9jFvmmcQRD0HyWfzRoTLNqzmDQqhUYd5Bj

