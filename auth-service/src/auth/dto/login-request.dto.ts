import { ApiProperty } from '@nestjs/swagger';
import { IsString, Length, MaxLength } from 'class-validator';

export class LoginRequestDto {
  @ApiProperty({ maxLength: 64 })
  @IsString()
  @Length(1, 64)
  username!: string;

  @ApiProperty({ maxLength: 128 })
  @IsString()
  @MaxLength(128)
  password!: string;
}
