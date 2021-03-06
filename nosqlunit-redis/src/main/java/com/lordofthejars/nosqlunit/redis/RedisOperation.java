package com.lordofthejars.nosqlunit.redis;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import redis.clients.jedis.Jedis;

import com.lordofthejars.nosqlunit.core.DatabaseOperation;
import com.lordofthejars.nosqlunit.redis.parser.DataReader;

public class RedisOperation implements DatabaseOperation<Jedis> {

	private Jedis jedis;
	private DataReader dataReader;
	
	public RedisOperation(Jedis jedis) {
		this.jedis = jedis;
		this.dataReader = new DataReader(jedis);
	}
	
	@Override
	public void insert(InputStream dataScript) {
		this.dataReader.read(dataScript);
	}

	@Override
	public void deleteAll() {
		this.jedis.flushAll();
	}

	@Override
	public boolean databaseIs(InputStream expectedData) {
		RedisAssertion.strictAssertEquals(new RedisConnectionCallback() {
			
			@Override
			public List<Jedis> getAllJedis() {
				return Arrays.asList(jedis);
			}
			
			@Override
			public Jedis getActiveJedis(byte[] key) {
				return jedis;
			}
		}, expectedData);
		return true;
	}

	@Override
	public Jedis connectionManager() {
		return jedis;
	}

}
